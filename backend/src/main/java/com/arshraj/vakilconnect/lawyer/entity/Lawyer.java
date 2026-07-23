package com.arshraj.vakilconnect.lawyer.entity;

import com.arshraj.vakilconnect.common.entity.BaseEntity;
import com.arshraj.vakilconnect.user.entity.User;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "lawyers")
public class Lawyer extends BaseEntity {

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "bar_council_number", nullable = false, unique = true)
    private String barCouncilNumber;

    @Column(name = "experience_years", nullable = false)
    private Integer experienceYears;

    @Column(length = 2000)
    private String bio;

    @Column(name = "consultation_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Column(nullable = false)
    private String city;

    @Column(name = "office_address", nullable = false)
    private String officeAddress;

    @Column(nullable = false)
    private Boolean verified = false;

    @Column(nullable = false)
    private Double rating = 0.0;

    @Column(name = "total_reviews", nullable = false)
    private Integer totalReviews = 0;

    public Lawyer() {
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getBarCouncilNumber() {
        return barCouncilNumber;
    }

    public void setBarCouncilNumber(String barCouncilNumber) {
        this.barCouncilNumber = barCouncilNumber;
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

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Integer getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(Integer totalReviews) {
        this.totalReviews = totalReviews;
    }
}