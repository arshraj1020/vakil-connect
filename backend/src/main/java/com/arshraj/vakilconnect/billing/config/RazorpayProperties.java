package com.arshraj.vakilconnect.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Razorpay credentials for the lawyer subscription feature.
 *
 * BOUND EXACTLY LIKE {@code AiProperties} - a validated record, registered
 * explicitly on the application class, with defaults living in
 * application.yaml as {@code ${ENV_VAR:}} rather than as {@code @DefaultValue}
 * here.
 *
 * EVERY FIELD IS BLANK BY DEFAULT, AND NONE IS {@code @NotBlank}. There is no
 * Razorpay account configured yet, so the application MUST start with all
 * three blank - the same zero-cost-by-default philosophy as
 * {@code vakilconnect.ai.*}. Unlike the AI block there is no "safe inert
 * provider" to fall back to; instead, {@code LawyerSubscriptionServiceImpl}
 * checks {@link #isConfigured()} itself and refuses to create a payment order
 * with a clear {@code BusinessRuleException} rather than letting a blank key
 * reach the Razorpay SDK and blow up unpredictably.
 *
 * keySecret AND webhookSecret ARE WHY THIS RECORD OVERRIDES toString() - see
 * AiProperties.apiKey for the identical hazard and fix.
 */
@Validated
@ConfigurationProperties(prefix = "vakilconnect.razorpay")
public record RazorpayProperties(

        /** Public key id, safe to hand to the frontend to open Razorpay Checkout. */
        String keyId,

        /** Secret key, used server-side only to create orders and never sent to the client. */
        String keySecret,

        /** Shared secret used to verify the `X-Razorpay-Signature` header on the webhook. */
        String webhookSecret
) {

    /** True once both the key id and secret needed to create a real order are present. */
    public boolean isConfigured() {
        return keyId != null && !keyId.isBlank()
                && keySecret != null && !keySecret.isBlank();
    }

    /** True once a webhook secret is set, i.e. the webhook signature can actually be checked. */
    public boolean isWebhookConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    @Override
    public String toString() {
        return "RazorpayProperties{keyId=" + keyId
                + ", keySecret=<redacted>"
                + ", webhookSecret=<redacted>}";
    }
}
