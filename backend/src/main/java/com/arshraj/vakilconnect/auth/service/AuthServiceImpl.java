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

    @Override
    public RegisterResponse register(RegisterRequest request) {

        String email = request.getEmail().trim().toLowerCase();

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

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
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