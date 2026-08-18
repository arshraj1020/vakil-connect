package com.arshraj.vakilconnect.auth.dto;

import com.arshraj.vakilconnect.lawyer.dto.CreateLawyerProfileRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name cannot exceed 150 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    /*
     * Constraints come from PasswordRules so registration and password reset
     * cannot drift apart. Values are unchanged - min 8, same messages - so this
     * is a refactor, not a behaviour change.
     */
    @NotBlank(message = PasswordRules.REQUIRED_MESSAGE)
    @Size(min = PasswordRules.MIN_LENGTH, message = PasswordRules.LENGTH_MESSAGE)
    private String password;

    @Pattern(
            regexp = "^\\+?[0-9]{10,15}$",
            message = "Invalid phone number"
    )
    private String phoneNumber;

    @Pattern(
            regexp = "CLIENT|LAWYER",
            message = "Role must be either CLIENT or LAWYER"
    )
    private String role;

    /**
     * Required only when {@code role == LAWYER}. Carries the professional
     * details needed to create the linked Lawyer profile atomically at signup.
     * Intentionally NOT annotated with {@code @Valid} so it stays optional for
     * clients; it is validated in the service when the role is LAWYER.
     */
    private CreateLawyerProfileRequest lawyerProfile;

    public RegisterRequest() {
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public CreateLawyerProfileRequest getLawyerProfile() {
        return lawyerProfile;
    }

    public void setLawyerProfile(CreateLawyerProfileRequest lawyerProfile) {
        this.lawyerProfile = lawyerProfile;
    }
}