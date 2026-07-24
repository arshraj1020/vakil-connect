package com.arshraj.vakilconnect.lawyer.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public class UpdateLawyerProfileRequest {

    @NotNull(message = "Experience is required")
    @Min(value = 0, message = "Experience cannot be negative")
    private Integer experienceYears;

    @NotBlank(message = "Bio is required")
    @Size(max = 2000, message = "Bio cannot exceed 2000 characters")
    private String bio;

    @NotNull(message = "Consultation fee is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Consultation fee must be greater than 0")
    private BigDecimal consultationFee;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "Office address is required")
    private String officeAddress;

    @NotNull(message = "At least one specialization is required")
    @Size(min = 1, message = "At least one specialization is required")
    private List<String> specializations;

    public UpdateLawyerProfileRequest() {
    }

    public Integer getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(Integer experienceYears) {
        this.experienceYears = experienceYears;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public BigDecimal getConsultationFee() {
        return consultationFee;
    }

    public void setConsultationFee(BigDecimal consultationFee) {
        this.consultationFee = consultationFee;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getOfficeAddress() {
        return officeAddress;
    }

    public void setOfficeAddress(String officeAddress) {
        this.officeAddress = officeAddress;
    }

    public List<String> getSpecializations() {
        return specializations;
    }

    public void setSpecializations(List<String> specializations) {
        this.specializations = specializations;
    }
}
