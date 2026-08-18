package com.arshraj.vakilconnect.user.repository;

import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
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

    /* ------------------------------------------ unverified purge (Phase 4) --
     *
     * Candidates for UnverifiedAccountPurgeJob. Scoped to CLIENT in the QUERY,
     * not filtered afterwards, so a LAWYER or ADMIN can never be loaded into
     * the deletion loop at all - the FK from `lawyers` has no ON DELETE
     * CASCADE, and an admin must be unreachable by an automated job.
     *
     * Returns candidates only: the job still checks appointments and reviews
     * per row before deleting anything.
     */
    List<User> findByEmailVerifiedFalseAndRoleAndCreatedAtBefore(
            Role role, LocalDateTime createdBefore);
}