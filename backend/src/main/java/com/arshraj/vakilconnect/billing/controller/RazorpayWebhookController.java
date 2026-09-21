package com.arshraj.vakilconnect.billing.controller;

import com.arshraj.vakilconnect.billing.service.LawyerSubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay's own server-to-server notification, e.g. "payment.captured".
 *
 * PUBLIC ON PURPOSE - Razorpay has no JWT to send, so this path is added to
 * SecurityConfig's permitAll list. Authenticity instead comes from the
 * `X-Razorpay-Signature` header, verified inside the service against the
 * configured webhook secret (see LawyerSubscriptionServiceImpl#handleWebhook).
 *
 * ALWAYS RETURNS 200. This is a best-effort safety net for when a lawyer
 * closes their browser mid-checkout and the normal /verify call from the
 * frontend never happens - the service method never throws, so there is
 * nothing here that would turn into a non-200 anyway. Returning anything
 * else risks Razorpay retrying the same event repeatedly.
 */
@RestController
@RequestMapping("/api/webhooks/razorpay")
public class RazorpayWebhookController {

    private final LawyerSubscriptionService subscriptionService;

    public RazorpayWebhookController(LawyerSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<Void> handle(@RequestBody String rawBody,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        subscriptionService.handleWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
