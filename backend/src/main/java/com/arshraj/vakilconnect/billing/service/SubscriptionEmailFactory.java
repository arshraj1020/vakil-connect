package com.arshraj.vakilconnect.billing.service;

import com.arshraj.vakilconnect.billing.entity.LawyerSubscription;
import com.arshraj.vakilconnect.billing.enums.SubscriptionPlan;
import com.arshraj.vakilconnect.email.EmailMessage;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Renders the purchase-confirmation email sent once a subscription is
 * activated - whether it was paid through Razorpay or made free by a
 * 100%-off coupon.
 *
 * LIVES IN billing/, NOT email/, mirroring identity's VerificationEmailFactory:
 * email/ owns transport and knows nothing about subscriptions or coupons;
 * this class knows the domain and nothing about Resend or HTTP.
 */
@Component
public class SubscriptionEmailFactory {

    public static final String TAG = "subscription-activated";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    public EmailMessage create(String recipientEmail, String fullName, LawyerSubscription subscription) {
        String greetingName = (fullName == null || fullName.isBlank()) ? "there" : fullName;
        String planLabel = subscription.getPlan() == SubscriptionPlan.YEARLY ? "Yearly" : "Monthly";
        String amountLine = amountLine(subscription);
        String couponLine = couponLine(subscription);
        LocalDateTime expiresAt = subscription.getExpiresAt();
        String expiresLine = expiresAt == null ? "" : expiresAt.format(DATE_FORMAT);

        String text = """
                Hi %s,

                Your VakilConnect %s subscription is now active.

                %s%s
                Valid until: %s

                Your profile is now listed on VakilConnect.

                - VakilConnect
                """.formatted(greetingName, planLabel.toLowerCase(), amountLine, couponLine, expiresLine);

        String html = """
                <p>Hi %s,</p>
                <p>Your VakilConnect %s subscription is now active.</p>
                <p>%s%s</p>
                <p>Valid until: <strong>%s</strong></p>
                <p>Your profile is now listed on VakilConnect.</p>
                <p>— VakilConnect</p>
                """.formatted(escape(greetingName), planLabel.toLowerCase(), escape(amountLine),
                escape(couponLine), expiresLine);

        return new EmailMessage(recipientEmail, "Your VakilConnect subscription is active", html, text, TAG);
    }

    private String amountLine(LawyerSubscription subscription) {
        int paise = subscription.getAmountPaise() == null ? 0 : subscription.getAmountPaise();
        if (paise == 0) {
            return "Amount paid: Free (100% off coupon)";
        }
        return "Amount paid: Rs " + (paise / 100.0 == Math.floor(paise / 100.0)
                ? String.valueOf(paise / 100)
                : String.format("%.2f", paise / 100.0));
    }

    private String couponLine(LawyerSubscription subscription) {
        String code = subscription.getCouponCode();
        if (code == null || code.isBlank()) {
            return "";
        }
        return "\nCoupon applied: " + code + " (" + subscription.getDiscountPercent() + "% off)\n";
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
