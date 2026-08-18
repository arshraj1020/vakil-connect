package com.arshraj.vakilconnect.auth.service;

import com.arshraj.vakilconnect.auth.dto.LoginRequest;
import com.arshraj.vakilconnect.auth.dto.LoginResponse;
import com.arshraj.vakilconnect.auth.dto.RegisterRequest;
import com.arshraj.vakilconnect.auth.dto.RegisterResponse;
import com.arshraj.vakilconnect.appointment.repository.AppointmentRepository;
import com.arshraj.vakilconnect.common.exception.DuplicateResourceException;
import com.arshraj.vakilconnect.common.exception.EmailNotVerifiedException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.identity.config.IdentityProperties;
import com.arshraj.vakilconnect.identity.entity.EmailTokenType;
import com.arshraj.vakilconnect.identity.service.EmailVerificationService;
import com.arshraj.vakilconnect.identity.service.VerificationTokenService;
import com.arshraj.vakilconnect.lawyer.dto.CreateLawyerProfileRequest;
import com.arshraj.vakilconnect.lawyer.service.LawyerService;
import com.arshraj.vakilconnect.review.repository.ReviewRepository;
import com.arshraj.vakilconnect.security.jwt.JwtService;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LawyerService lawyerService;

    /* ------------------------------------------------------- Phase 4 wiring --
     * Registration now issues a verification token and queues its email, and
     * defends the email address against squatting. None of this changes the
     * login path, which is untouched below.
     */
    private final EmailVerificationService emailVerificationService;
    private final VerificationTokenService verificationTokenService;
    private final IdentityProperties identityProperties;
    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtService jwtService,
                           LawyerService lawyerService,
                           EmailVerificationService emailVerificationService,
                           VerificationTokenService verificationTokenService,
                           IdentityProperties identityProperties,
                           AppointmentRepository appointmentRepository,
                           ReviewRepository reviewRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.lawyerService = lawyerService;
        this.emailVerificationService = emailVerificationService;
        this.verificationTokenService = verificationTokenService;
        this.identityProperties = identityProperties;
        this.appointmentRepository = appointmentRepository;
        this.reviewRepository = reviewRepository;
    }

    /**
     * The single normalisation rule for an email address entering this service.
     *
     * `User.setEmail` applies the same rule on write, so the database only ever
     * holds trimmed lowercase. Every lookup therefore has to normalise too, or
     * it silently fails to match a row that is plainly there - which is exactly
     * the defect this method was extracted to fix (see `login`).
     *
     * Kept null-safe to mirror `User.setEmail`. In practice `@NotBlank` on the
     * DTOs means null never reaches here, but a normaliser that NPEs on null is
     * a trap for the next caller.
     *
     * Locale.ROOT for the same reason as `User.setEmail`: the no-arg
     * toLowerCase() would use the JVM default locale, and Turkish lowercases 'I'
     * to the dotless 'ı'. The write rule and this read rule must produce
     * byte-identical output on every host, so both pin the locale.
     */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public RegisterResponse register(RegisterRequest request) {

        String email = normalizeEmail(request.getEmail());

        Role role = Role.CLIENT;
        if (request.getRole() != null && !request.getRole().isBlank()) {
            role = Role.valueOf(request.getRole().trim().toUpperCase());
        }

        // For lawyer signups, validate the professional details up front so that
        // nothing is persisted if they are missing (the whole method is @Transactional).
        if (role == Role.LAWYER) {
            validateLawyerProfile(request.getLawyerProfile());
        }

        User existing = userRepository.findByEmail(email).orElse(null);

        User savedUser = (existing == null)
                ? createUser(request, email, role)
                : takeOverOrReject(existing, request, role);

        /*
         * Issue the verification token and QUEUE the email inside this same
         * transaction.
         *
         * The token row must be atomic with the user row - a token for a user
         * that failed to persist is meaningless. The email is only PUBLISHED
         * here; Phase 3's AFTER_COMMIT listener performs the actual send once
         * this transaction commits, so a rollback dispatches nothing.
         */
        emailVerificationService.sendVerificationEmail(savedUser);

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getRole().name(),
                "Registration successful. Check your email to verify your account."
        );
    }

    /** The ordinary path: a brand-new account, unverified by entity default. */
    private User createUser(RegisterRequest request, String email, Role role) {
        User user = new User();

        user.setFullName(request.getFullName());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(role);
        // emailVerified defaults to false on the entity - not set here, so the
        // default stays the single source of that fact.

        User savedUser = userRepository.save(user);

        // Atomically create the linked Lawyer profile in the same transaction.
        if (role == Role.LAWYER) {
            lawyerService.createLawyerProfile(savedUser.getEmail(), request.getLawyerProfile());
        }

        return savedUser;
    }

    /**
     * EMAIL SQUATTING DEFENCE (TDD D8/R1).
     *
     * `users.email` is UNIQUE and registration does not require verification,
     * so without this an attacker could register somebody else's address, never
     * verify it, and block the real owner permanently.
     *
     * A re-registration therefore CLAIMS the existing row - overwriting name,
     * phone and password hash - but only when EVERY condition holds:
     *
     *   * the account is not verified. A verified account is somebody's, full
     *     stop.
     *   * it is older than TAKEOVER_THRESHOLD (7 days). Deliberately far longer
     *     than the 24h token TTL: a token expiring does not mean the account is
     *     abandoned, and a short window would turn a one-off block into a
     *     repeatable grief loop against a slow but legitimate user.
     *   * the existing role is CLIENT. A LAWYER carries a lawyer_profiles row
     *     with a unique bar_council_number, so claiming it would mean deciding
     *     what happens to somebody's professional credentials.
     *   * NO DEPENDENT ROWS: zero appointments and zero reviews. Any activity at
     *     all means a real person used this account.
     *
     * NARROWED DELIBERATELY: the INCOMING role must also be CLIENT. The TDD
     * specifies the existing role but is silent on the new one, and letting a
     * LAWYER registration claim a CLIENT row would mutate the role and require
     * creating a professional profile on a stranger's row. Refusing is the
     * conservative reading and costs a squatted lawyer nothing they cannot get
     * by using a different address.
     *
     * Anything that fails a condition falls through to the pre-existing 409, so
     * ordinary duplicate registration is completely unchanged.
     */
    private User takeOverOrReject(User existing, RegisterRequest request, Role role) {

        if (!isTakeoverEligible(existing, role)) {
            throw new DuplicateResourceException("Email already exists.");
        }

        /*
         * Invalidate the previous owner's outstanding links BEFORE issuing new
         * credentials. If the old verification email were still live, whoever
         * received it could verify an account whose password now belongs to
         * somebody else.
         */
        verificationTokenService.invalidateAll(existing.getId(), EmailTokenType.VERIFY_EMAIL);
        verificationTokenService.invalidateAll(existing.getId(), EmailTokenType.RESET_PASSWORD);

        existing.setFullName(request.getFullName());
        existing.setPhoneNumber(request.getPhoneNumber());
        existing.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        existing.setEmailVerified(false);

        /*
         * The credentials just changed hands. Moving this forward is what will
         * invalidate any JWT issued to the previous holder once the claim check
         * lands in a later phase - the column is already here, so setting it
         * now costs nothing and closes the gap the day that check ships.
         */
        existing.setCredentialsChangedAt(Instant.now());

        log.info("Unverified account {} claimed by re-registration after the takeover window",
                existing.getId());

        return userRepository.save(existing);
    }

    private boolean isTakeoverEligible(User existing, Role incomingRole) {
        if (existing.isEmailVerified()) {
            return false;
        }
        if (existing.getRole() != Role.CLIENT || incomingRole != Role.CLIENT) {
            return false;
        }
        if (!existing.isActive()) {
            // An admin deactivated this account. Reclaiming it would undo a
            // moderation decision.
            return false;
        }

        Instant cutoff = Instant.now().minus(identityProperties.takeoverThreshold());
        Instant createdAt = existing.getCreatedAt() == null
                ? Instant.EPOCH
                : existing.getCreatedAt().toInstant(ZoneOffset.UTC);

        if (createdAt.isAfter(cutoff)) {
            return false;
        }

        return appointmentRepository.countByClient(existing) == 0
                && reviewRepository.countByClient(existing) == 0;
    }

    /**
     * Ensures a LAWYER registration carries the professional details required to
     * build a valid Lawyer entity. Throws a 400 (IllegalArgumentException) listing
     * any missing fields, so registration fails cleanly instead of hitting a
     * database NOT NULL / unique violation.
     */
    private void validateLawyerProfile(CreateLawyerProfileRequest profile) {
        if (profile == null) {
            throw new IllegalArgumentException(
                    "Lawyer registration requires a 'lawyerProfile' with professional details.");
        }

        List<String> missing = new ArrayList<>();
        if (profile.getBarCouncilNumber() == null || profile.getBarCouncilNumber().isBlank()) {
            missing.add("barCouncilNumber");
        }
        if (profile.getExperienceYears() == null) {
            missing.add("experienceYears");
        }
        if (profile.getConsultationFee() == null) {
            missing.add("consultationFee");
        }
        if (profile.getCity() == null || profile.getCity().isBlank()) {
            missing.add("city");
        }
        if (profile.getOfficeAddress() == null || profile.getOfficeAddress().isBlank()) {
            missing.add("officeAddress");
        }
        if (profile.getSpecializations() == null || profile.getSpecializations().isEmpty()) {
            missing.add("specializations");
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Lawyer registration is missing required fields: " + String.join(", ", missing));
        }
    }

    @Override
    public LoginResponse login(LoginRequest request) {

        /*
         * Normalise ONCE, then use the same value for both steps.
         *
         * This previously passed the raw request value to authenticate() while
         * normalising only for findByEmail. Because User.setEmail lowercases on
         * write, the database holds lowercase exclusively, so
         * DaoAuthenticationProvider -> loadUserByUsername -> findByEmail(raw)
         * found nothing and anyone who typed "Foo@Bar.com" was rejected with a
         * 401 for a password that was correct. The normalised lookup below was
         * never reached, which is why the bug was invisible in this method.
         *
         * Normalising here rather than in CustomUserDetailsService is deliberate:
         * login is the only place an untrusted, arbitrarily-cased address enters
         * the system. Every other caller of loadUserByUsername passes a JWT
         * subject, which is User.getEmail() and therefore already normalised.
         * Normalising in the lookup as well would hide a future caller that
         * forgot to, instead of failing loudly.
         */
        String email = normalizeEmail(request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        email,
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        /*
         * EMAIL VERIFICATION GATE (Phase 7).
         *
         * ORDER MATTERS: this runs AFTER authenticate() has already verified
         * the password. Checking first would let an unauthenticated prober
         * discover which addresses have unverified accounts without knowing any
         * password - the check would become an enumeration oracle in its own
         * right. By the time we reach here the caller has proved they own the
         * credentials, so telling them their own account needs verifying
         * discloses nothing they did not already know.
         *
         * DELIBERATELY NOT IN JwtAuthenticationFilter. That filter answers "is
         * this token still good?"; this answers "may this account obtain a
         * token at all?". Two different questions. Putting verification in the
         * filter would also re-check it on every request for a token that could
         * only exist because login already allowed it.
         *
         * DELIBERATELY NOT IN CustomUserDetailsService.disabled(). That flag is
         * `users.active` - admin deactivation - and conflating the two would
         * collapse "an admin disabled you" and "click the link we emailed you"
         * into one indistinguishable DisabledException, leaving the frontend
         * unable to decide between a support link and a resend button.
         *
         * GRANDFATHERED USERS ARE UNAFFECTED. V7 backfilled every pre-existing
         * account to is_email_verified = true, so the gate only ever sees
         * accounts created after that migration. Admins are created verified by
         * AdminBootstrapRunner, and a password reset sets the flag too - so no
         * role is structurally locked out.
         *
         * Flag-gated so it can be switched off in seconds without a deploy if
         * email delivery degrades.
         */
        if (identityProperties.verificationEnforced() && !user.isEmailVerified()) {
            log.info("Login refused for unverified account {}", user.getId());
            throw new EmailNotVerifiedException();
        }

        /*
         * The token is bound to the credential state it is minted under, so a
         * later password change or account takeover invalidates it.
         *
         * Read from the user row loaded a moment ago in this same transaction -
         * so it cannot be stale relative to the credentials that just
         * authenticated.
         */
        String token = jwtService.generateToken(user.getEmail(), user.getCredentialsChangedAt());

        return new LoginResponse(
                token,
                "Bearer",
                user.getFullName(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}