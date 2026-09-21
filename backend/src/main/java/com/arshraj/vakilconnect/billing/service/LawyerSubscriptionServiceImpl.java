package com.arshraj.vakilconnect.billing.service;

import com.arshraj.vakilconnect.billing.config.RazorpayProperties;
import com.arshraj.vakilconnect.billing.dto.SubscriptionOrderResponse;
import com.arshraj.vakilconnect.billing.dto.SubscriptionStatusResponse;
import com.arshraj.vakilconnect.billing.entity.LawyerSubscription;
import com.arshraj.vakilconnect.billing.enums.SubscriptionPlan;
import com.arshraj.vakilconnect.billing.enums.SubscriptionStatus;
import com.arshraj.vakilconnect.billing.repository.LawyerSubscriptionRepository;
import com.arshraj.vakilconnect.common.exception.BusinessRuleException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.lawyer.repository.LawyerRepository;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Razorpay-backed subscriptions: a lawyer pays Rs 500/month or Rs 5500/year
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

    public LawyerSubscriptionServiceImpl(LawyerSubscriptionRepository subscriptionRepository,
                                          LawyerRepository lawyerRepository,
                                          UserRepository userRepository,
                                          RazorpayProperties razorpayProperties) {
        this.subscriptionRepository = subscriptionRepository;
        this.lawyerRepository = lawyerRepository;
        this.userRepository = userRepository;
        this.razorpayProperties = razorpayProperties;
    }

    @Override
    @Transactional
    public SubscriptionStatusResponse getCurrentStatus(String userEmail) {
        Lawyer lawyer = lawyerForEmail(userEmail);

        Optional<LawyerSubscription> current =
                subscriptionRepository.findTopByLawyerIdOrderByCreatedAtDesc(lawyer.getId());

        if (current.isEmpty()) {
            return new SubscriptionStatusResponse(null, null, false, null, null);
        }

        return toStatusResponse(current.get());
    }

    @Override
    @Transactional
    public SubscriptionOrderResponse createOrder(String userEmail, SubscriptionPlan plan) {
        if (!razorpayProperties.isConfigured()) {
            throw new BusinessRuleException("Payments are not configured yet.");
        }

        Lawyer lawyer = lawyerForEmail(userEmail);

        try {
            RazorpayClient client =
                    new RazorpayClient(razorpayProperties.keyId(), razorpayProperties.keySecret());

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", plan.getAmountPaise());
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "sub_" + lawyer.getId() + "_" + System.currentTimeMillis());

            com.razorpay.Order order = client.orders.create(orderRequest);
            String orderId = order.get("id");

            LawyerSubscription subscription = new LawyerSubscription();
            subscription.setLawyer(lawyer);
            subscription.setPlan(plan);
            subscription.setStatus(SubscriptionStatus.PENDING);
            subscription.setAmountPaise(plan.getAmountPaise());
            subscription.setCurrency("INR");
            subscription.setRazorpayOrderId(orderId);
            subscriptionRepository.save(subscription);

            return new SubscriptionOrderResponse(
                    orderId, razorpayProperties.keyId(), plan.getAmountPaise(), "INR", plan.name());

        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create Razorpay order for lawyer {}", lawyer.getId(), e);
            throw new BusinessRuleException("Could not start the payment. Please try again.");
        }
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
