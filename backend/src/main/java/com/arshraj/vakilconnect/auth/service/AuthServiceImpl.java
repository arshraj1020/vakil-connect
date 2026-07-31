package com.arshraj.vakilconnect.auth.service;

import com.arshraj.vakilconnect.auth.dto.LoginRequest;
import com.arshraj.vakilconnect.auth.dto.LoginResponse;
import com.arshraj.vakilconnect.auth.dto.RegisterRequest;
import com.arshraj.vakilconnect.auth.dto.RegisterResponse;
import com.arshraj.vakilconnect.common.exception.DuplicateResourceException;
import com.arshraj.vakilconnect.common.exception.ResourceNotFoundException;
import com.arshraj.vakilconnect.lawyer.dto.CreateLawyerProfileRequest;
import com.arshraj.vakilconnect.lawyer.service.LawyerService;
import com.arshraj.vakilconnect.security.jwt.JwtService;
import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LawyerService lawyerService;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           JwtService jwtService,
                           LawyerService lawyerService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.lawyerService = lawyerService;
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

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email already exists.");
        }

        Role role = Role.CLIENT;
        if (request.getRole() != null && !request.getRole().isBlank()) {
            role = Role.valueOf(request.getRole().trim().toUpperCase());
        }

        // For lawyer signups, validate the professional details up front so that
        // nothing is persisted if they are missing (the whole method is @Transactional).
        if (role == Role.LAWYER) {
            validateLawyerProfile(request.getLawyerProfile());
        }

        User user = new User();

        user.setFullName(request.getFullName());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(role);

        User savedUser = userRepository.save(user);

        // Atomically create the linked Lawyer profile in the same transaction.
        if (role == Role.LAWYER) {
            lawyerService.createLawyerProfile(savedUser.getEmail(), request.getLawyerProfile());
        }

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getRole().name(),
                "Registration successful."
        );
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

        String token = jwtService.generateToken(user.getEmail());

        return new LoginResponse(
                token,
                "Bearer",
                user.getFullName(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}