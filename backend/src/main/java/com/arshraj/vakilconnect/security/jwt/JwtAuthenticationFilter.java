package com.arshraj.vakilconnect.security.jwt;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);
            String email = jwtService.extractUsername(token);

            Authentication authentication = SecurityContextHolder
                    .getContext()
                    .getAuthentication();

            if (email != null && authentication == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(email);

                /*
                 * isEnabled() must be checked here explicitly.
                 *
                 * CustomUserDetailsService builds the principal with
                 * .disabled(!user.isActive()), but that flag is only enforced by
                 * DaoAuthenticationProvider, which runs during login and nowhere
                 * else. Authenticating straight from the UserDetails - as this
                 * filter does - bypasses it entirely, so without this check a
                 * deactivated account kept full API access until its token
                 * expired, up to 24 hours later.
                 *
                 * The current value is already loaded on every request, so this
                 * makes deactivation take effect on the very next call.
                 */
                if (jwtService.isTokenValid(token, userDetails.getUsername())
                        && userDetails.isEnabled()) {

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    SecurityContextHolder.getContext()
                            .setAuthentication(authToken);
                }
            }

        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ignored) {
            /*
             * Invalid token, expired token, or a well-formed token whose subject
             * no longer exists (a deleted account).
             *
             * UsernameNotFoundException is included deliberately: it is an
             * AuthenticationException rather than a JwtException, so it used to
             * escape this filter uncaught and surface as a 500. Leaving the
             * SecurityContext empty instead lets the chain continue and the
             * entry point answer 401, which is what the caller should see.
             */
        }

        filterChain.doFilter(request, response);
    }
}