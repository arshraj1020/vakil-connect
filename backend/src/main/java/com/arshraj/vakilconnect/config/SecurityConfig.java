package com.arshraj.vakilconnect.config;

import com.arshraj.vakilconnect.security.handler.RestAccessDeniedHandler;
import com.arshraj.vakilconnect.security.handler.RestAuthenticationEntryPoint;
import com.arshraj.vakilconnect.security.jwt.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /** Comma-separated list; defaults to the local Next.js dev server. */
    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .cors(Customizer.withDefaults())

                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",

                                /*
                                 * Email verification (Phase 4). Public by
                                 * necessity, not convenience: a user who cannot
                                 * log in yet must still be able to verify, and
                                 * one whose email never arrived must be able to
                                 * ask for another.
                                 *
                                 * Enumerated individually rather than as
                                 * /api/auth/** so that any future auth endpoint
                                 * is authenticated by default and has to be
                                 * opened deliberately.
                                 *
                                 * Neither reads the SecurityContext; authority
                                 * comes from possession of the token, which is
                                 * single-use and expiring.
                                 */
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification",

                                /*
                                 * Password reset (Phase 6). Public for the same
                                 * structural reason: somebody who has forgotten
                                 * their password cannot authenticate in order
                                 * to ask for a new one.
                                 *
                                 * Authority comes from possession of a
                                 * single-use, expiring token delivered to the
                                 * account's own mailbox - not from the session.
                                 */
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",

                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/lawyers/**")
                        .permitAll()

                        /*
                         * Reference data (countries, states, cities, languages,
                         * specializations) is public by necessity, not by
                         * convenience: registration needs these lists BEFORE an
                         * account exists, so requiring a token would make signup
                         * impossible. The data is a curated vocabulary and
                         * discloses nothing about any user.
                         *
                         * GET only - there is no write path, and scoping the
                         * matcher to the verb means adding one later has to be a
                         * deliberate security decision rather than an accident.
                         */
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/reference/**")
                        .permitAll()

                        .requestMatchers("/api/client/**")
                        .hasRole("CLIENT")

                        .requestMatchers("/api/lawyer/**")
                        .hasRole("LAWYER")

                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .httpBasic(httpBasic -> httpBasic.disable());

        return http.build();
    }

    /**
     * CORS for the browser frontend. Origins are explicit (never "*").
     *
     * allowCredentials is intentionally left off: authentication uses a Bearer
     * token in the Authorization header, not cookies, so credentialed requests
     * are unnecessary and enabling them would only widen the policy.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}