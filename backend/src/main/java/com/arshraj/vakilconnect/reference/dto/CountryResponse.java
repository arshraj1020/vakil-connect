package com.arshraj.vakilconnect.reference.dto;

import java.util.UUID;

/**
 * A country, as offered to a client building a location picker.
 *
 * Only India is seeded today, so this list has exactly one entry. The endpoint
 * exists anyway: a single-option response is honest about what the platform
 * supports, and it lets the frontend hide the control rather than hardcode the
 * assumption.
 */
public class CountryResponse {

    private UUID id;
    private String iso2;
    private String name;
    private String phoneCode;

    public CountryResponse() {
    }

    public CountryResponse(UUID id, String iso2, String name, String phoneCode) {
        this.id = id;
        this.iso2 = iso2;
        this.name = name;
        this.phoneCode = phoneCode;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getIso2() {
        return iso2;
    }

    public void setIso2(String iso2) {
        this.iso2 = iso2;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneCode() {
        return phoneCode;
    }

    public void setPhoneCode(String phoneCode) {
        this.phoneCode = phoneCode;
    }
}
