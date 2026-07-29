package com.arshraj.vakilconnect.lawyer.entity;

import com.arshraj.vakilconnect.common.entity.BaseEntity;
import com.arshraj.vakilconnect.reference.entity.City;
import com.arshraj.vakilconnect.reference.entity.Language;
import com.arshraj.vakilconnect.user.entity.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

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

    @ManyToMany
    @JoinTable(
            name = "lawyer_specializations",
            joinColumns = @JoinColumn(name = "lawyer_id"),
            inverseJoinColumns = @JoinColumn(name = "specialization_id")
    )
    private Set<Specialization> specializations = new HashSet<>();

    /* ------------------------------------------------- reference data (V4) --
     * Added in Phase 2B. Nothing reads these yet: the free-text `city` column
     * above is still authoritative until the backfill and cut-over phases.
     *
     * All three are LAZY. `specializations` above is LAZY too (@ManyToMany
     * defaults to it), and that is exactly what caused the Phase 1 defect -
     * verifyLawyer mapped a detached entity to a DTO outside a transaction and
     * returned 500 after the write had already committed. Three more lazy
     * associations on this class make that easier to reintroduce, so any
     * service method touching them must be @Transactional, and any query
     * returning a page of lawyers must JOIN FETCH rather than let Hibernate
     * resolve them per row.
     *
     * No cascade, deliberately: cities and languages are shared reference rows.
     * A cascade here would let deleting one lawyer delete Mumbai.
     */

    /**
     * The city shown wherever a lawyer is displayed as being in one place.
     *
     * Nullable until backfill completes. Option C keeps this ALONGSIDE the
     * practice set rather than deriving it: with no primary, a search result
     * card would have to pick one of several cities arbitrarily.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_city_id")
    private City primaryCity;

    /**
     * Every city this lawyer practises in - the axis lawyer SEARCH queries.
     *
     * The primary city is expected to be a member of this set. That invariant
     * belongs to the service layer and is NOT enforced in Phase 2B, because
     * this phase adds no service code; at the persistence layer the two are
     * currently independent.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "lawyer_practice_cities",
            joinColumns = @JoinColumn(name = "lawyer_id"),
            inverseJoinColumns = @JoinColumn(name = "city_id")
    )
    private Set<City> practiceCities = new HashSet<>();

    /** Languages this lawyer can consult in. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "lawyer_languages",
            joinColumns = @JoinColumn(name = "lawyer_id"),
            inverseJoinColumns = @JoinColumn(name = "language_id")
    )
    private Set<Language> languages = new HashSet<>();

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

    public Set<Specialization> getSpecializations() {
        return specializations;
    }

    public void setSpecializations(Set<Specialization> specializations) {
        this.specializations = specializations;
    }

    public City getPrimaryCity() {
        return primaryCity;
    }

    public void setPrimaryCity(City primaryCity) {
        this.primaryCity = primaryCity;
    }

    public Set<City> getPracticeCities() {
        return practiceCities;
    }

    public void setPracticeCities(Set<City> practiceCities) {
        this.practiceCities = practiceCities;
    }

    public Set<Language> getLanguages() {
        return languages;
    }

    public void setLanguages(Set<Language> languages) {
        this.languages = languages;
    }
}
