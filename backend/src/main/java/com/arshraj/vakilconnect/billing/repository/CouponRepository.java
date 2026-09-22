package com.arshraj.vakilconnect.billing.repository;

import com.arshraj.vakilconnect.billing.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponRepository extends JpaRepository<Coupon, UUID> {

    /**
     * Case-insensitive on purpose - a lawyer typing "arsh10" should not be
     * told the code is invalid just because the seed data stored it upper
     * case.
     */
    Optional<Coupon> findByCodeIgnoreCaseAndActiveTrue(String code);
}
