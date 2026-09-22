package com.arshraj.vakilconnect.billing.config;

import com.arshraj.vakilconnect.billing.entity.Coupon;
import com.arshraj.vakilconnect.billing.repository.CouponRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates/updates subscription coupons at startup from the COUPON_CODES
 * environment variable, the same way {@code AdminBootstrapRunner} creates the
 * admin account from ADMIN_EMAIL / ADMIN_PASSWORD.
 *
 * WHY NOT A FLYWAY SEED: a coupon code is effectively a password - anyone who
 * knows it gets a discount, up to a free subscription for a 100%-off code -
 * and this repository is public. A Flyway migration is a plain SQL file
 * committed to git, so seeding real codes there would publish them on GitHub
 * forever (even a later migration renaming them leaves the original codes
 * readable in the file's git history). Reading them from an environment
 * variable set only in Render's dashboard keeps every real code out of
 * version control entirely.
 *
 * Format: comma-separated "CODE:PERCENT" pairs, e.g.
 * "WELCOME10:10,FRIEND20:20,FREEONE:100". Blank/unset does nothing - opt-in,
 * like AdminBootstrapRunner. Idempotent and safe to run on every boot: an
 * existing code is upserted (percent/active refreshed) rather than
 * duplicated, so rotating a code's discount is a Render env var edit plus a
 * redeploy, no migration needed.
 */
@Component
public class CouponBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CouponBootstrapRunner.class);

    private final CouponRepository couponRepository;
    private final String couponCodesRaw;

    public CouponBootstrapRunner(CouponRepository couponRepository,
                                  @Value("${COUPON_CODES:}") String couponCodesRaw) {
        this.couponRepository = couponRepository;
        this.couponCodesRaw = couponCodesRaw;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (couponCodesRaw == null || couponCodesRaw.isBlank()) {
            // Opt-in: no COUPON_CODES set, nothing to bootstrap.
            return;
        }

        int created = 0;
        int updated = 0;

        for (String entry : couponCodesRaw.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts = trimmed.split(":");
            if (parts.length != 2) {
                log.warn("Skipping malformed COUPON_CODES entry (expected CODE:PERCENT): {}", trimmed);
                continue;
            }

            String code = parts[0].trim().toUpperCase();
            int percent;
            try {
                percent = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                log.warn("Skipping COUPON_CODES entry with a non-numeric percent: {}", trimmed);
                continue;
            }
            if (code.isEmpty() || percent < 1 || percent > 100) {
                log.warn("Skipping COUPON_CODES entry outside the valid 1-100 percent range: {}", trimmed);
                continue;
            }

            Coupon coupon = couponRepository.findByCodeIgnoreCaseAndActiveTrue(code)
                    .or(() -> couponRepository.findAll().stream()
                            .filter(c -> c.getCode().equalsIgnoreCase(code))
                            .findFirst())
                    .orElseGet(Coupon::new);

            boolean isNew = coupon.getId() == null;
            coupon.setCode(code);
            coupon.setDiscountPercent(percent);
            coupon.setActive(true);
            couponRepository.save(coupon);

            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        if (created > 0 || updated > 0) {
            log.info("Coupon bootstrap: {} created, {} updated.", created, updated);
        }
    }
}
