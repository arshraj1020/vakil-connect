package com.arshraj.vakilconnect.billing.dto;

import java.time.LocalDateTime;

/**
 * The authenticated lawyer's current subscription state (GET /api/lawyer/subscription).
 *
 * `active` is COMPUTED (status == ACTIVE && expiresAt is in the future), not a
 * raw copy of the stored status - a row can be ACTIVE in the database but
 * already past its expiry with no sweep job having run yet.
 *
 * `plan`/`status`/`startsAt`/`expiresAt` are all null when the lawyer has
 * never subscribed (no row exists yet).
 */
public class SubscriptionStatusResponse {

    private String plan;
    private String status;
    private boolean active;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;

    public SubscriptionStatusResponse() {
    }

    public SubscriptionStatusResponse(String plan, String status, boolean active,
                                       LocalDateTime startsAt, LocalDateTime expiresAt) {
        this.plan = plan;
        this.status = status;
        this.active = active;
        this.startsAt = startsAt;
        this.expiresAt = expiresAt;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(LocalDateTime startsAt) {
        this.startsAt = startsAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
}
