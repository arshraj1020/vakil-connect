package com.arshraj.vakilconnect.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Guarded by springdoc's OWN property rather than a profile: when the docs
 * endpoint is off (application-prod.yaml) this bean has no consumer, and a
 * config class that builds API metadata in an environment that must not serve
 * API metadata is an inconsistency even if nothing reads it.
 * matchIfMissing = true preserves today's default-on behaviour everywhere the
 * property is unset.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled",
        havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {

        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("VakilConnect API")
                        .version("1.0")
                        .description("Backend APIs for VakilConnect"))
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(
                                securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ));
    }
}