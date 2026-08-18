package com.arshraj.vakilconnect.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    /**
     * Name of the credential-change claim. Short, because it is carried on
     * every request.
     */
    public static final String CLAIM_CREDENTIALS_CHANGED_AT = "cca";

    /**
     * Issues a token bound to the credential state it was minted under.
     *
     * `cca` is epoch MILLISECONDS, not seconds. Second granularity would let a
     * token minted in the same second as a credential change survive the
     * comparison, and "the attacker's session died one second too late" is not
     * a property worth shipping.
     *
     * This method only WRITES the claim. Deciding whether a presented claim is
     * still acceptable is account state, not cryptography, so it lives in
     * JwtAuthenticationFilter beside the other account-state check. Keeping
     * this class a pure codec is what stops authorization logic from ending up
     * in two places.
     *
     * @param credentialsChangedAt the user's current value; never null - the
     *                             column is NOT NULL and the entity initialises
     *                             it at construction
     */
    public String generateToken(String email, Instant credentialsChangedAt) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .claim(CLAIM_CREDENTIALS_CHANGED_AT, credentialsChangedAt.toEpochMilli())
                .signWith(secretKey)
                .compact();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token)
                .getExpiration()
                .before(new Date());
    }

    public boolean isTokenValid(String token, String email) {
        return email.equals(extractUsername(token))
                && !isTokenExpired(token);
    }

    /**
     * The `cca` claim as an Instant, or empty when the token has no usable one.
     *
     * RETURNS EMPTY RATHER THAN THROWING for both absent and malformed values,
     * because the caller treats them identically - as "not authenticatable" -
     * and an Optional makes that a total function instead of a try/catch at the
     * call site.
     *
     * Absent covers every JWT issued before this claim existed. Malformed
     * covers a token whose payload was tampered with... which cannot actually
     * happen, since the signature is verified before this is ever reached. It
     * is handled anyway: a NumberFormatException escaping into the filter would
     * surface as a 500, and an authentication path must never answer 500 to a
     * bad credential.
     */
    public Optional<Instant> extractCredentialsChangedAt(String token) {
        Object raw = extractAllClaims(token).get(CLAIM_CREDENTIALS_CHANGED_AT);

        if (raw == null) {
            return Optional.empty();
        }

        try {
            // Jackson deserialises a JSON number as Integer or Long depending on
            // magnitude, and a tampered token could carry a String. Normalising
            // through Number covers the first two; the parse covers the rest.
            long epochMillis = (raw instanceof Number number)
                    ? number.longValue()
                    : Long.parseLong(raw.toString().trim());

            return Optional.of(Instant.ofEpochMilli(epochMillis));
        } catch (NumberFormatException | ArithmeticException e) {
            return Optional.empty();
        }
    }
}