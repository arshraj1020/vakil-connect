package com.arshraj.vakilconnect.reference.dto;

import java.util.UUID;

/**
 * A practice area.
 *
 * Read-only in this phase. Registration still resolves specializations by NAME
 * with find-or-create, so this endpoint currently reports what exists rather
 * than constraining what may be submitted. Tightening that to resolve-or-reject
 * is a later phase and would change registration behaviour, which is explicitly
 * out of scope here.
 */
public class SpecializationResponse {

    private UUID id;
    private String name;

    public SpecializationResponse() {
    }

    public SpecializationResponse(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
