package com.arshraj.vakilconnect.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * Serializes paged responses through Spring Data's PagedModel DTO instead of
 * PageImpl.
 *
 * PageImpl's JSON structure is an implementation detail and Spring Data warns
 * that its stability is not guaranteed across versions. VIA_DTO produces a
 * documented, stable envelope ({ content, page: { size, number, totalElements,
 * totalPages } }), which the frontend can safely be typed against.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig {
}
