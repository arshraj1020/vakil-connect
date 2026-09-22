package com.arshraj.vakilconnect.billing.entity;

import com.arshraj.vakilconnect.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A percentage-off code a lawyer can apply when subscribing (see V13).
 *
 * `discountPercent` is 1-100. 100 means the subscription is free -
 * {@link com.arshraj.vakilconnect.billing.service.LawyerSubscriptionServiceImpl#createOrder}
 * skips Razorpay entirely for those, since Razorpay does not support a
 * zero-amount order.
 */
@Entity
@Table(name = "coupons")
public class Coupon extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "discount_percent", nullable = false)
    private Integer discountPercent;

    @Column(nullable = false)
    private boolean active = true;

    public Coupon() {
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getDiscountPercent() {
        return discountPercent;
    }

    public void setDiscountPercent(Integer discountPercent) {
        this.discountPercent = discountPercent;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
