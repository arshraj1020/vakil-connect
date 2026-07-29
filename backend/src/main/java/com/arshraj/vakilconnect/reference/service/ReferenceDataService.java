package com.arshraj.vakilconnect.reference.service;

import com.arshraj.vakilconnect.reference.dto.CityResponse;
import com.arshraj.vakilconnect.reference.dto.CountryResponse;
import com.arshraj.vakilconnect.reference.dto.LanguageResponse;
import com.arshraj.vakilconnect.reference.dto.SpecializationResponse;
import com.arshraj.vakilconnect.reference.dto.StateResponse;

import java.util.List;
import java.util.UUID;

/**
 * Read-only access to the curated vocabularies.
 *
 * Every method returns ACTIVE rows only. Reference rows are deactivated rather
 * than deleted, and a deactivated row must disappear from pickers - but note
 * the deliberate asymmetry: lawyer SEARCH must still match a deactivated city,
 * or retiring one would silently hide every lawyer in it.
 */
public interface ReferenceDataService {

    List<CountryResponse> getCountries();

    /** States of one country, by ISO 3166-1 alpha-2 code. */
    List<StateResponse> getStates(String countryIso2);

    /** Cities of one state - the dependent dropdown. */
    List<CityResponse> getCities(UUID stateId);

    /**
     * Typeahead across city names AND historical aliases.
     *
     * Alias coverage is the point: "Bangalore", "Bombay" and "Gurgaon" are
     * still what people type, and without them a dropdown is a worse experience
     * than the free text it replaces.
     */
    List<CityResponse> searchCities(String query, int limit);

    List<LanguageResponse> getLanguages();

    List<SpecializationResponse> getSpecializations();
}
