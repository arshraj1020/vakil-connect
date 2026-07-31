package com.arshraj.vakilconnect.config;

import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates a single ADMIN user at startup from environment variables, since ADMIN
 * is intentionally not obtainable through the public registration API.
 *
 * Idempotent and opt-in:
 *   - does nothing if ADMIN_EMAIL / ADMIN_PASSWORD are not set;
 *   - does nothing if a user with ADMIN_EMAIL already exists.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final String adminEmail;
    private final String adminPassword;
    private final String adminFullName;
    private final String adminPhoneNumber;

    public AdminBootstrapRunner(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${ADMIN_EMAIL:}") String adminEmail,
                                @Value("${ADMIN_PASSWORD:}") String adminPassword,
                                @Value("${ADMIN_FULL_NAME:}") String adminFullName,
                                @Value("${ADMIN_PHONE_NUMBER:}") String adminPhoneNumber) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminFullName = adminFullName;
        this.adminPhoneNumber = adminPhoneNumber;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (isBlank(adminEmail) || isBlank(adminPassword)) {
            // Opt-in: no credentials provided, nothing to bootstrap.
            return;
        }

        String email = adminEmail.trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            log.info("Bootstrap admin already exists.");
            return;
        }

        User admin = new User();
        admin.setFullName(isBlank(adminFullName) ? "Administrator" : adminFullName.trim());
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setPhoneNumber(isBlank(adminPhoneNumber) ? null : adminPhoneNumber.trim());
        admin.setRole(Role.ADMIN);
        admin.setActive(true);

        // The bootstrap admin has no mailbox to verify from - this account is the
        // only way into the system, so it is created already verified.
        admin.setEmailVerified(true);

        userRepository.save(admin);

        log.info("Bootstrap admin created: {}", email);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
