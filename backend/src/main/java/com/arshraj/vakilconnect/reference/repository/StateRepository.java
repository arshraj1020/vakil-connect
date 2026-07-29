package com.arshraj.vakilconnect.reference.repository;

import com.arshraj.vakilconnect.reference.entity.State;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StateRepository extends JpaRepository<State, UUID> {

    /** Backs the country -> state dropdown. */
    List<State> findByCountryIso2IgnoreCaseAndActiveTrueOrderByNameAsc(String iso2);

    /** `code` is unique only within a country, so both are required. */
    Optional<State> findByCountryIso2IgnoreCaseAndCodeIgnoreCase(String iso2, String code);

    Optional<State> findByCountryIso2IgnoreCaseAndNameNormalized(String iso2, String nameNormalized);

    long countByCountryIso2IgnoreCase(String iso2);
}
