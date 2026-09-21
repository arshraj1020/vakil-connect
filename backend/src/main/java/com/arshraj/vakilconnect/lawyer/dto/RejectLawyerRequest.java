package com.arshraj.vakilconnect.lawyer.dto;

/**
 * Body for PUT /api/admin/lawyers/{id}/reject. `reason` is optional and free
 * text - it is shown back to the lawyer so they know what to fix before
 * resubmitting (editing their profile clears the rejection automatically).
 */
public class RejectLawyerRequest {

    private String reason;

    public RejectLawyerRequest() {
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
