package com.arshraj.vakilconnect.reference.dto;

import java.util.UUID;

/**
 * A state or union territory.
 *
 * `type` is exposed so the UI can group the two rather than presenting 36
 * undifferentiated entries - conflating them is factually wrong on an
 * India-first platform.
 */
public class StateResponse {

    private UUID id;
    private String code;
    private String name;
    /** STATE or UNION_TERRITORY. */
    private String type;

    public StateResponse() {
    }

    public StateResponse(UUID id, String code, String name, String type) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
