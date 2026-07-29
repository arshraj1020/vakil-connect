package com.arshraj.vakilconnect.reference.repository;

import com.arshraj.vakilconnect.reference.entity.Language;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LanguageRepository extends JpaRepository<Language, UUID> {

    Optional<Language> findByIsoCodeIgnoreCase(String isoCode);

    List<Language> findByActiveTrueOrderByNameAsc();
}
