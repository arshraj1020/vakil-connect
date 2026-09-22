package com.arshraj.vakilconnect.billing.service;

import com.arshraj.vakilconnect.billing.config.RazorpayProperties;
import com.arshraj.vakilconnect.billing.dto.CouponValidationResponse;
import com.arshraj.vakilconnect.billing.dto.SubscriptionOrderResponse;
import com.arshraj.vakilconnect.billing.dto.SubscriptionStatusResponse;
import com.arshraj.vakilconnect.billing.entity.Coupon;
import com.arshraj.vakilconnect.billing.entity.LawyerSubscription;
import com.arshraj.vakilconnect.billing.enums.SubscriptionPlan;
import com.arshraj.vakilconnect.billing.enums.SubscriptionStatus;
import com.arshraj.vakilconnect.billing.repository.CouponRepository;
import com.arshraj.vakilconnect.billing.repository.LawyerSubscriptionRepository;
import com.arshraj.vakilconnect.common.exception.BusinessRuleException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import com.arshraj.vakilconnect.email.EmailMessage;
import com.arshraj.vakilconnect.email.event.SendEmailRequestedEvent;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.List;
import org.json.JSONArray;

/**
 * Razorpay-backed subscriptions: a lawyer pays Rs 499/month or Rs 5499/year
 * to join the platform.
 *
 * SIGNATURE VERIFICATION IS MANUAL (HMAC-SHA256), NOT THE SDK HELPER. Both the
 * order-verify flow and the webhook use the same {@link #hmacSha256Hex}
 * rather than reaching for whatever verification helper `razorpay-java`
 * exposes, because that keeps the one piece of this feature that is a real
 * security boundary - "did this payment actually come from Razorpay" -
 * readable and independently checkable against Razorpay's own documented
 * algorithm (HMAC-SHA256 of a known string, hex digest, constant-time
 * compare), instead of trusting an SDK method's exact contract.
 *
 * RazorpayClient IS CONSTRUCTED PER CALL, NEVER AS A SPRING BEAN. A `@Bean`
 * would be built eagerly at application startup and would need real
 * credentials to exist at all - exactly the crash-with-blank-keys failure
 * {@link RazorpayProperties} is designed to avoid. Constructing it inside
 * {@link #createOrder} means the only path that needs Razorpay reachable is
 * the one already guarded by {@link RazorpayProperties#isConfigured()}.
 */
@Service
public class LawyerSubscriptionServiceImpl implements LawyerSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(LawyerSubscriptionServiceImpl.class);

    private final LawyerSubscriptionRepository subscriptionRepository;
    private final LawyerRepository lawyerRepository;
    private final UserRepository userRepository;
    private final RazorpayProperties razorpayProperties;
    private final CouponRepository couponRepository;
    private final SubscriptionEmailFactory emailFactory;
    private final ApplicationEventPublisher eventPublisher;

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public LawyerSubscriptionServiceImpl(LawyerSubscriptionRepository subscriptionRepository,
                                          LawyerRepository lawyerRepository,
                                          UserRepository userRepository,
                                          RazorpayProperties razorpayProperties,
                                          CouponRepository couponRepository,
                                          SubscriptionEmailFactory emailFactory,
                                          ApplicationEventPublisher eventPublisher) {
        this.subscriptionRepository = subscriptionRepository;
        this.lawyerRepository = lawyerRepository;
        this.userRepository = userRepository;
        this.razorpayProperties = razorpayProperties;
        this.couponRepository = couponRepository;
        this.emailFactory = emailFactory;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public SubscriptionStatusResponse getCurrentStatus(String userEmail) {
        Lawyer lawyer = lawyerForEmail(userEmail);

        List<LawyerSubscription> subscriptions =
                subscriptionRepository.findByLawyerIdOrderByCreatedAtDesc(lawyer.getId());

        if (subscriptions.isEmpty()) {
            return new SubscriptionStatusResponse(null, null, false, null, null);
        }

        // Self-heal every PENDING row, not just the newest one. A lawyer can
        // end up with more than one PENDING order - a re-click of Subscribe
        // before the first order's payment settled, an abandoned Checkout,
        // a stale attempt from testing - and the payment that actually
        // succeeded is not guaranteed to belong to the most recent row. Each
        // reconcile call is read-only against Razorpay and a no-op if that
        // particular order has no captured payment yet, so sweeping all of
        // them is safe. (This also covers the original bug: the browser's
        // own /verify call never happening because a UPI app-switch on
        // mobile never returns focus to Checkout's `handler` callback, even
        // though Razorpay itself captured the payment.)
        for (LawyerSubscription subscription : subscriptions) {
            if (subscription.getStatus() == SubscriptionStatus.PENDING) {
                reconcileWithRazorpay(subscription);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LawyerSubscription chosen = subscriptions.stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE
                        && s.getExpiresAt() != null
                        && s.getExpiresAt().isAfter(now))
                .findFirst() // list is newest-first, so this is the most recent active one
                .orElse(subscriptions.get(0));

        return toStatusResponse(chosen);
    }

    @Override
    @Transactional
    public SubscriptionOrderResponse createOrder(String userEmail, SubscriptionPlan plan, String couponCode) {
        if (!razorpayProperties.isConfigured()) {
            throw new BusinessRuleException("Payments are not configured yet.");
        }

        Lawyer lawyer = lawyerForEmail(userEmail);

        // Self-heal any PENDING orders before deciding whether a new one is
        // needed - otherwise a lawyer who already paid (but whose payment
        // hadn't reconciled yet) could end up stacking a second, redundant
        // order on top of one that was actually fine.
        List<LawyerSubscription> existing =
                subscriptionRepository.findByLawyerIdOrderByCreatedAtDesc(lawyer.getId());
        for (LawyerSubscription subscription : existing) {
            if (subscription.getStatus() == SubscriptionStatus.PENDING) {
                reconcileWithRazorpay(subscription);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        boolean alreadyActive = existing.stream().anyMatch(s -> s.getStatus() == SubscriptionStatus.ACTIVE
                && s.getExpiresAt() != null
                && s.getExpiresAt().isAfter(now));
        if (alreadyActive) {
            throw new BusinessRuleException("You already have an active subscription.");
        }

        Coupon coupon = resolveCoupon(couponCode);
        int discountPercent = coupon == null ? 0 : coupon.getDiscountPercent();
        String resolvedCouponCode = coupon == null ? null : coupon.getCode();

        int originalAmountPaise = plan.getAmountPaise();
        int discountedAmountPaise = originalAmountPaise - (originalAmountPaise * discountPercent / 100);

        // A 100%-off coupon leaves nothing for Razorpay to charge - it does
        // not support a zero-amount order - so skip Checkout entirely and
        // activate the subscription the same way a verified payment would.
        if (discountPercent >= 100) {
            LawyerSubscription subscription = new LawyerSubscription();
            subscription.setLawyer(lawyer);
            subscription.setPlan(plan);
            subscription.setAmountPaise(0);
            subscription.setCurrency("INR");
            subscription.setCouponCode(resolvedCouponCode);
            subscription.setDiscountPercent(discountPercent);
            activate(subscription, "coupon-" + resolvedCouponCode, "n/a");

            return new SubscriptionOrderResponse(
                    null, null, 0, originalAmountPaise, discountPercent, "INR", plan.name(), false);
        }

        try {
            RazorpayClient client =
                    new RazorpayClient(razorpayProperties.keyId(), razorpayProperties.keySecret());

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", discountedAmountPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "sub_" + lawyer.getId() + "_" + System.currentTimeMillis());

            com.razorpay.Order order = client.orders.create(orderRequest);
            String orderId = order.get("id");

            LawyerSubscription subscription = new LawyerSubscription();
            subscription.setLawyer(lawyer);
            subscription.setPlan(plan);
            subscription.setStatus(SubscriptionStatus.PENDING);
            subscription.setAmountPaise(discountedAmountPaise);
            subscription.setCurrency("INR");
            subscription.setRazorpayOrderId(orderId);
            subscription.setCouponCode(resolvedCouponCode);
            subscription.setDiscountPercent(discountPercent);
            subscriptionRepository.save(subscription);

            return new SubscriptionOrderResponse(orderId, razorpayProperties.keyId(), discountedAmountPaise,
                    originalAmountPaise, discountPercent, "INR", plan.name(), true);

        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create Razorpay order for lawyer {}", lawyer.getId(), e);
            throw new BusinessRuleException("Could not start the payment. Please try again.");
        }
    }

    @Override
    public CouponValidationResponse validateCoupon(String couponCode) {
        Coupon coupon = resolveCoupon(couponCode);
        if (coupon == null) {
            throw new BusinessRuleException("Invalid coupon code.");
        }
        return new CouponValidationResponse(coupon.getCode(), coupon.getDiscountPercent());
    }

    /** @return the active coupon for this code, or null when couponCode is blank/absent. Throws when non-blank but not found/active. */
    private Coupon resolveCoupon(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return null;
        }
        return couponRepository.findByCodeIgnoreCaseAndActiveTrue(couponCode.trim())
                .orElseThrow(() -> new BusinessRuleException("Invalid coupon code."));
    }

    @Override
    @Transactional
    public SubscriptionStatusResponse verifyPayment(String userEmail, String orderId,
                                                      String paymentId, String signature) {
        if (!razorpayProperties.isConfigured()) {
            throw new BusinessRuleException("Payments are not configured yet.");
        }

        Lawyer lawyer = lawyerForEmail(userEmail);

        LawyerSubscription subscription = subscriptionRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription order not found"));

        if (!subscription.getLawyer().getId().equals(lawyer.getId())) {
            throw new ResourceNotFoundException("Subscription order not found");
        }

        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            // Already verified (e.g. the webhook beat the browser callback to
            // it) - idempotent success rather than an error.
            return toStatusResponse(subscription);
        }

        String expectedSignature = hmacSha256Hex(orderId + "|" + paymentId, razorpayProperties.keySecret());
        if (!constantTimeEquals(expectedSignature, signature)) {
            throw new BusinessRuleException("Payment verification failed.");
        }

        activate(subscription, paymentId, signature);
        return toStatusResponse(subscription);
    }

    @Override
    @Transactional
    public void handleWebhook(String rawBody, String signatureHeader) {
        if (!razorpayProperties.isWebhookConfigured()) {
            // No secret to verify against - nothing safe to do with this.
            return;
        }

        try {
            if (signatureHeader == null) {
                log.warn("Razorpay webhook received with no signature header, ignoring");
                return;
            }

            String expected = hmacSha256Hex(rawBody, razorpayProperties.webhookSecret());
            if (!constantTimeEquals(expected, signatureHeader)) {
                log.warn("Razorpay webhook signature mismatch, ignoring payload");
                return;
            }

            JSONObject payload = new JSONObject(rawBody);
            String event = payload.optString("event", "");
            if (!"payment.captured".equals(event)) {
                return;
            }

            JSONObject paymentEntity = payload
                    .optJSONObject("payload")
                    .optJSONObject("payment")
                    .optJSONObject("entity");
            if (paymentEntity == null) {
                return;
            }

            String orderId = paymentEntity.optString("order_id", null);
            String paymentId = paymentEntity.optString("id", null);
            if (orderId == null || paymentId == null) {
                return;
            }

            Optional<LawyerSubscription> subscriptionOpt =
                    subscriptionRepository.findByRazorpayOrderId(orderId);
            if (subscriptionOpt.isEmpty()) {
                return;
            }

            LawyerSubscription subscription = subscriptionOpt.get();
            if (subscription.getStatus() == SubscriptionStatus.PENDING) {
                activate(subscription, paymentId, signatureHeader);
            }

        } catch (Exception e) {
            // Best-effort: a webhook must never 500 on a payload shape we did
            // not anticipate, or Razorpay will retry-storm the endpoint.
            log.warn("Failed to process Razorpay webhook", e);
        }
    }

    /**
     * Asks Razorpay directly whether a PENDING order has a captured payment,
     * and activates the subscription if so.
     *
     * USES THE ORDERS API, NOT A SIGNATURE. The signature check in
     * {@link #verifyPayment} exists because that call is client-submitted -
     * anyone could POST arbitrary orderId/paymentId/signature triples, so the
     * signature is what proves the triple really came from Razorpay. This
     * method instead ASKS Razorpay's server directly, over a connection
     * authenticated with our own key secret, so there is nothing for a
     * signature to attest to: the response itself is the source of truth.
     *
     * A plain HTTP call rather than the SDK, for the same reason
     * {@link #hmacSha256Hex} is hand-rolled: this is a small, well-documented
     * REST endpoint (GET /v1/orders/{id}/payments, HTTP Basic auth with
     * key_id:key_secret), and a direct call is easier to verify by reading
     * than trusting an SDK method's exact behaviour.
     *
     * Never throws - called from a read path (getCurrentStatus) that must
     * keep working even when Razorpay is briefly unreachable.
     */
    private void reconcileWithRazorpay(LawyerSubscription subscription) {
        String orderId = subscription.getRazorpayOrderId();
        if (orderId == null) {
            return;
        }

        try {
            String credentials = razorpayProperties.keyId() + ":" + razorpayProperties.keySecret();
            String basicAuth = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.razorpay.com/v1/orders/" + orderId + "/payments"))
                    .header("Authorization", "Basic " + basicAuth)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Razorpay orders/payments lookup for {} returned HTTP {}", orderId, response.statusCode());
                return;
            }

            JSONArray items = new JSONObject(response.body()).optJSONArray("items");
            if (items == null) {
                return;
            }

            for (int i = 0; i < items.length(); i++) {
                JSONObject payment = items.getJSONObject(i);
                if ("captured".equals(payment.optString("status"))) {
                    activate(subscription, payment.getString("id"), "reconciled-via-orders-api");
                    return;
                }
            }

        } catch (Exception e) {
            log.warn("Could not reconcile subscription order {} with Razorpay", orderId, e);
        }
    }

    private void activate(LawyerSubscription subscription, String paymentId, String signature) {
        LocalDateTime now = LocalDateTime.now();

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setRazorpayPaymentId(paymentId);
        subscription.setRazorpaySignature(signature);
        subscription.setStartsAt(now);
        subscription.setExpiresAt(subscription.getPlan() == SubscriptionPlan.YEARLY
                ? now.plusYears(1)
                : now.plusMonths(1));

        subscriptionRepository.save(subscription);

        sendActivationEmail(subscription);
    }

    /**
     * Best-effort: a purchase confirmation email must never fail the
     * activation itself. Published as an event (AFTER_COMMIT, see
     * SendEmailRequestedEvent) rather than sent inline, matching how every
     * other transactional email in this codebase is dispatched - so the
     * email only ever goes out once this subscription row has actually
     * committed.
     */
    private void sendActivationEmail(LawyerSubscription subscription) {
        try {
            User user = subscription.getLawyer().getUser();
            EmailMessage message = emailFactory.create(user.getEmail(), user.getFullName(), subscription);
            eventPublisher.publishEvent(new SendEmailRequestedEvent(message));
        } catch (Exception e) {
            log.warn("Could not queue subscription activation email for subscription {}",
                    subscription.getId(), e);
        }
    }

    private Lawyer lawyerForEmail(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return lawyerRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Lawyer profile not found"));
    }

    private SubscriptionStatusResponse toStatusResponse(LawyerSubscription subscription) {
        boolean active = subscription.getStatus() == SubscriptionStatus.ACTIVE
                && subscription.getExpiresAt() != null
                && subscription.getExpiresAt().isAfter(LocalDateTime.now());

        return new SubscriptionStatusResponse(
                subscription.getPlan().name(),
                subscription.getStatus().name(),
                active,
                subscription.getStartsAt(),
                subscription.getExpiresAt());
    }

    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
