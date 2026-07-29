package com.arshraj.vakilconnect.user.repository;

import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Page<User> findByRole(Role role, Pageable pageable);

    long countByRole(Role role);

    /* ------------------------------------------ reconciliation (Phase 2F) --
     * Both are expected to equal the total user count: `users` has never held a
     * city or a language, so there is no legacy data to backfill from. They are
     * reported anyway, so the gap stays visible rather than being mistaken for
     * a completed migration.
     */

    long countByCityIsNull();

    long countByPreferredLanguageIsNull();
}