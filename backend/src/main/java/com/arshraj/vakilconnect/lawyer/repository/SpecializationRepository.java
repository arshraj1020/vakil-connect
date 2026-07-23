package com.arshraj.vakilconnect.lawyer.repository;

import com.arshraj.vakilconnect.lawyer.entity.Specialization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpecializationRepository extends JpaRepository<Specialization, UUID> {

    Optional<Specialization> findByNameIgnoreCase(String name);
}
