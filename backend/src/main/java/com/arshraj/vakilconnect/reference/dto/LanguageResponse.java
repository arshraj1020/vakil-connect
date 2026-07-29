package com.arshraj.vakilconnect.reference.dto;

import java.util.UUID;

/**
 * A language.
 *
 * `nativeName` is not decoration - a Marathi speaker scanning a dropdown should
 * see "मराठी", and on an India-first platform that is the difference between a
 * usable control and a list of English exonyms.
 */
public class LanguageResponse {

    private UUID id;
    private String isoCode;
    private String name;
    private String nativeName;

    public LanguageResponse() {
    }

    public LanguageResponse(UUID id, String isoCode, String name, String nativeName) {
        this.id = id;
        this.isoCode = isoCode;
        this.name = name;
        this.nativeName = nativeName;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getIsoCode() {
        return isoCode;
    }

    public void setIsoCode(String isoCode) {
        this.isoCode = isoCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNativeName() {
        return nativeName;
    }

    public void setNativeName(String nativeName) {
        this.nativeName = nativeName;
    }
}
