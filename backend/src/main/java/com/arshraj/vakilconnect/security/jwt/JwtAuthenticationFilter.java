package com.arshraj.vakilconnect.security.jwt;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

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
                 *
                 * READ THE RECEIVER CAREFULLY. This is Spring Security's
                 * UserDetails.isEnabled(), which here means `users.active` -
                 * admin deactivation. It is NOT email verification. The entity
                 * field for that is User.isEmailVerified(), and it is checked in
                 * AuthServiceImpl.login(), not here: a token can only exist if
                 * login already issued one, so re-checking verification on every
                 * request would buy nothing.
                 */
                /*
                 * CREDENTIAL-CHANGE INVALIDATION (Phase 5).
                 *
                 * A JWT is bound to the credential state it was minted under.
                 * If the account's credentials have moved on since, the token
                 * is stale and must not authenticate - that is what makes a
                 * password change or an account takeover actually end the
                 * previous holder's session, rather than merely stopping them
                 * logging in again.
                 *
                 * NO EXTRA QUERY. `credentialsChangedAt` rides on the principal
                 * that loadUserByUsername already returned above, from the row
                 * that was already read.
                 *
                 * WHY THE COMPARISON IS TRUSTWORTHY. The claim is attacker-
                 * readable but not attacker-writable: the signature is verified
                 * before this point, so a forged or edited `cca` fails
                 * parseSignedClaims and never reaches here. The value it is
                 * compared against comes from the database, not the token.
                 *
                 * ABSENT CLAIM IS TREATED AS STALE, deliberately. Every JWT
                 * issued before this phase lacks `cca`; admitting them would
                 * leave a window in which the mechanism does nothing - for
                 * exactly the sessions most likely to be compromised. The cost
                 * is a one-time sign-out at deploy, which is documented.
                 */
                boolean credentialsCurrent =
                        hasCurrentCredentials(token, userDetails);

                if (jwtService.isTokenValid(token, userDetails.getUsername())
                        && userDetails.isEnabled()
                        && credentialsCurrent) {

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

    /**
     * True when the token was minted under the account's CURRENT credentials.
     *
     * Rejects three cases, all as ordinary authentication failure - the caller
     * simply leaves the SecurityContext empty and the entry point answers 401.
     * None of them may produce a 500: an authentication path that throws on a
     * bad credential turns a routine rejection into an incident page.
     *
     *   * ABSENT `cca`  - every pre-Phase-5 token. Treated as stale.
     *   * MALFORMED `cca` - unreachable in practice, since the signature is
     *     verified first, but handled so a parse failure can never escape.
     *   * STALE `cca`   - the credentials changed after this token was issued.
     *
     * COMPARISON: reject when `claim < stored`. Strictly less-than, so a token
     * minted in the same millisecond as the change is still honoured - the
     * legitimate user who just changed their password and was handed a fresh
     * token must not be locked out by a tie. Milliseconds rather than seconds
     * make that window a millisecond wide instead of a second.
     *
     * Only ever passed an AuthenticatedUser, because CustomUserDetailsService
     * is the sole producer of principals here. The type check is defensive: a
     * future UserDetails that cannot report its credential state must fail
     * CLOSED rather than silently skip the check.
     */
    private boolean hasCurrentCredentials(String token, UserDetails userDetails) {
        if (!(userDetails instanceof AuthenticatedUser authenticatedUser)) {
            log.warn("Principal for {} is not an AuthenticatedUser; "
                            + "rejecting because credential state cannot be verified",
                    userDetails.getUsername());
            return false;
        }

        /*
         * BOTH SIDES MUST BE COMPARED AT MILLISECOND PRECISION.
         *
         * `credentials_changed_at` is timestamptz - PostgreSQL stores
         * MICROSECONDS - while the claim is written with toEpochMilli(), which
         * FLOORS to milliseconds. Comparing the floored claim against the
         * unfloored stored value makes every freshly issued token look stale
         * the instant the stored value has any sub-millisecond remainder, which
         * is essentially always:
         *
         *     stored ...123456us  ->  ...123456000ns
         *     claim  ...123ms     ->  ...123000000ns   -> "before" -> rejected
         *
         * Truncating the stored value to milliseconds puts both sides in the
         * same units. It is exact rather than approximate: tokens are only
         * issued at login, which re-reads the user inside its own transaction,
         * so the claim and this comparison derive from the SAME persisted
         * microsecond value and floor to the same millisecond.
         *
         * This does NOT widen the check. A credential change still invalidates
         * the token as soon as it lands in a later millisecond; the only thing
         * now tolerated is a change in the very same millisecond as issuance,
         * which is the "equal timestamps are valid" rule already agreed.
         */
        Instant storedAtClaimPrecision = authenticatedUser
                .getCredentialsChangedAt()
                .truncatedTo(ChronoUnit.MILLIS);

        // No token contents are logged anywhere in this method - a JWT in a log
        // line is a usable credential.
        return jwtService.extractCredentialsChangedAt(token)
                .map(claimed -> !claimed.isBefore(storedAtClaimPrecision))
                .orElse(false);
    }
}