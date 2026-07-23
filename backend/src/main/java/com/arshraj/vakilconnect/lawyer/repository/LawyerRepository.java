package com.arshraj.vakilconnect.lawyer.repository;

import com.arshraj.vakilconnect.lawyer.entity.Lawyer;
import com.arshraj.vakilconnect.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LawyerRepository extends JpaRepository<Lawyer, UUID> {

    Optional<Lawyer> findByUser(User user);

    Optional<Lawyer> findByBarCouncilNumber(String barCouncilNumber);

    boolean existsByUser(User user);

    boolean existsByBarCouncilNumber(String barCouncilNumber);
}