package com.arshraj.vakilconnect.reference.dto;

import java.util.UUID;

/**
 * A city, always carrying its state.
 *
 * The state is included even on the dependent-dropdown endpoint, where the
 * caller already knows it. That is deliberate: search results are rendered as
 * "Pune, Maharashtra" for disambiguation - several Indian city names repeat
 * across states - and one shape for both endpoints means the frontend needs one
 * component, not two.
 */
public class CityResponse {

    private UUID id;
    private String name;
    private UUID stateId;
    private String stateCode;
    private String stateName;

    public CityResponse() {
    }

    public CityResponse(UUID id, String name, UUID stateId, String stateCode, String stateName) {
        this.id = id;
        this.name = name;
        this.stateId = stateId;
        this.stateCode = stateCode;
        this.stateName = stateName;
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

    public UUID getStateId() {
        return stateId;
    }

    public void setStateId(UUID stateId) {
        this.stateId = stateId;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName;
    }
}
