package com.arshraj.vakilconnect.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * ETag support, scoped to the reference endpoints.
 *
 * `ShallowEtagHeaderFilter` buffers a response, hashes it, and answers 304 when
 * the client's `If-None-Match` matches. That is ideal for reference data - a
 * few hundred bytes of near-static JSON that every client requests on load -
 * and wrong for everything else, since buffering a response to hash it costs
 * memory and latency on endpoints that will never produce a repeat hit.
 *
 * Hence the URL pattern rather than a global registration: existing endpoints
 * keep their current behaviour exactly.
 *
 * `Cache-Control` is set per-endpoint in the controller rather than here, so the
 * freshness policy lives next to the data it describes.
 */
@Configuration
public class ReferenceHttpCacheConfig {

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> referenceEtagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());

        registration.addUrlPatterns("/api/reference/*");
        registration.setName("referenceEtagFilter");

        return registration;
    }
}
