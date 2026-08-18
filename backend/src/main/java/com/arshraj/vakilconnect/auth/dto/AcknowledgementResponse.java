package com.arshraj.vakilconnect.auth.dto;

/**
 * A deliberately uninformative acknowledgement.
 *
 * EXISTS PRECISELY SO THAT NOTHING CAN LEAK. Returning a status-bearing type
 * from resend-verification would invite a future change that reports whether
 * the address was found - which is the account-enumeration hole the endpoint is
 * designed to avoid. This type has one field and it is the same string every
 * time.
 */
public class AcknowledgementResponse {

    private String message;

    public AcknowledgementResponse() {
    }

    public AcknowledgementResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
