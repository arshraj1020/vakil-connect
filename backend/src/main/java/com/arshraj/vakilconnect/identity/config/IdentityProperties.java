package com.arshraj.vakilconnect.identity.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Every tunable for email verification and password reset, in one bound object.
 *
 * WHY A BOUND RECORD RATHER THAN @Value. The rest of this codebase reads single
 * settings with @Value and an inline default (see ReferenceMigrationMetrics).
 * That works for one key. This feature has ten, consumed by five different
 * services, and scattering them would mean the default for a token lifetime
 * lives in whichever class happened to need it first - with no single place to
 * read the feature's configuration surface.
 *
 * DEFAULTS LIVE IN application.yaml, NOT HERE. Each key is declared there as
 * ${ENV_VAR:default}, which keeps one visible, greppable, environment-
 * overridable source. Repeating them as @DefaultValue would create two
 * declarations that can silently drift.
 *
 * The corollary is that a key missing from application.yaml would bind to null
 * and only fail later, at the point of use, as an NPE with no clue where it
 * came from. @Validated plus @NotNull turns that into a startup failure naming
 * the property - the same fail-fast posture as JWT_SECRET.
 *
 * Registered explicitly via @EnableConfigurationProperties on the application
 * class rather than @ConfigurationPropertiesScan: one line, no classpath
 * scanning, and the registration is visible where the application is defined.
 *
 * Flat rather than nested. Ten keys do not need a hierarchy, and a flat record
 * has no nested-binding edge cases to get wrong.
 */
@Validated
@ConfigurationProperties(prefix = "vakilconnect.identity")
public record IdentityProperties(

        /*
         * HMAC key for token hashing, applied by TokenHasher before a token is
         * stored so that a leaked database dump alone yields nothing usable.
         *
         * NOW REQUIRED. TokenHasher exists, so an empty pepper would mean
         * hashing under a known-empty key - worse than not hashing, because it
         * looks protected. The application refuses to start instead, matching
         * the JWT_SECRET posture.
         *
         * TOKEN_PEPPER must be present in every environment BEFORE this code is
         * deployed. It was set on Render ahead of this change for exactly that
         * reason; the test profile supplies its own in application-test.yaml.
         *
         * Rotating it invalidates every outstanding verification and reset link.
         */
        @NotBlank
        String tokenPepper,

        /*
         * Base URL of the FRONTEND, not the API - verification and reset links
         * point at Next.js pages, which then POST to the backend. Getting this
         * wrong sends every user in production to localhost.
         */
        @NotBlank
        String publicBaseUrl,

        /*
         * The login gate. False means tokens are issued and emails are sent but
         * unverified users can still log in, so delivery can be proven in
         * production before anything is gated on it. Flipping it is a
         * configuration change, not a deploy - which is also the fastest
         * rollback this feature has.
         */
        boolean verificationEnforced,

        /* 24h: long enough to survive "I'll do it tonight", short enough that a
         * link in an abandoned inbox is not indefinitely live. Resend is cheap,
         * so the cost of expiry is low. */
        @NotNull
        Duration verificationTokenTtl,

        /* 30m, not 15. Mail delivery lag plus a user reading on a phone twenty
         * minutes later is an ordinary sequence, and every expiry pushes them
         * through the flow again - which sends another email and erodes the
         * security benefit it was supposed to buy. */
        @NotNull
        Duration resetTokenTtl,

        /* Minimum gap between verification emails for one account. Enforced
         * from the newest row in email_tokens rather than an in-memory counter,
         * so a restart or a deploy cannot reset it and be used to mail-bomb
         * somebody. */
        @NotNull
        Duration resendCooldown,

        /*
         * How long an unverified account is protected before a re-registration
         * may claim its email address.
         *
         * Deliberately decoupled from verificationTokenTtl. A token expiring
         * does not mean the account is abandoned - the owner can still request
         * a new link - so tying takeover to token expiry would make a slow but
         * legitimate user vulnerable to a repeatable grief loop.
         */
        @NotNull
        Duration takeoverThreshold,

        /*
         * The scheduled purge runs on every instance it is enabled on, and the
         * deletes are idempotent, so a second replica would merely duplicate
         * the work. Disable it everywhere but one until there is a leader
         * election. Same single-instance assumption as the rate limiter.
         */
        boolean purgeEnabled,

        /* How long consumed and superseded tokens are kept before the purge
         * removes them. They are the audit trail for "who asked for a reset,
         * from where, and did they use it". */
        @NotNull
        Duration tokenPurgeRetention,

        /* How long an unverified account survives before the purge removes it.
         * Must exceed takeoverThreshold, or an address would be purged before a
         * legitimate re-registration could ever claim it. */
        @NotNull
        Duration unverifiedPurgeAfter
) {
}
