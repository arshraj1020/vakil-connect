package com.arshraj.vakilconnect.reference.repository;

import com.arshraj.vakilconnect.reference.entity.Country;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CountryRepository extends JpaRepository<Country, UUID> {

    /** Lookup by the natural business key (ISO 3166-1 alpha-2). */
    Optional<Country> findByIso2IgnoreCase(String iso2);

    /** Pickers show only active rows; search must not filter on `active`. */
    List<Country> findByActiveTrueOrderByNameAsc();
}
