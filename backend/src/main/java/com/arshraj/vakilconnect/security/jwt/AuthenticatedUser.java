package com.arshraj.vakilconnect.security.jwt;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * A {@link UserDetails} that also carries the account's credential-change
 * timestamp.
 *
 * WHY THIS TYPE EXISTS. Spring's own
 * {@code org.springframework.security.core.userdetails.User.withUsername(...)}
 * builder produces a fixed shape with no room for an extra field, and the
 * filter needs `credentialsChangedAt` on every request. Loading it separately
 * would mean a second query for a value the same row already supplied, so it
 * rides along on the principal instead.
 *
 * ============================ isEnabled() ============================
 *
 * READ THIS BEFORE CHANGING ANYTHING HERE.
 *
 * {@code isEnabled()} means `users.active` - an ADMIN DEACTIVATION - and
 * nothing else. It is NOT email verification. The previous implementation
 * expressed that as {@code .disabled(!user.isActive())} on Spring's builder;
 * this class must express the identical thing, and the constructor therefore
 * takes `active` directly and returns it unchanged.
 *
 * JwtAuthenticationFilter enforces this per request, so getting the polarity
 * wrong here does one of two things, both severe:
 *
 *   * inverted -> every deactivated account regains full API access, and every
 *     healthy account is locked out;
 *   * wired to emailVerified -> a login gate ships silently, years before the
 *     phase that is supposed to introduce it.
 *
 * The remaining three account-status flags stay {@code true}, exactly as
 * Spring's builder left them: this application models neither expiry nor
 * locking, and returning anything else would invent a state nothing sets.
 */
public class AuthenticatedUser implements UserDetails {

    private final String username;
    private final String password;
    private final boolean active;
    private final Instant credentialsChangedAt;
    private final List<GrantedAuthority> authorities;

    public AuthenticatedUser(String username,
                             String password,
                             boolean active,
                             Instant credentialsChangedAt,
                             List<GrantedAuthority> authorities) {
        this.username = username;
        this.password = password;
        this.active = active;
        this.credentialsChangedAt = credentialsChangedAt;
        this.authorities = List.copyOf(authorities);
    }

    /**
     * When this account's credentials last changed.
     *
     * Compared against the token's `cca` claim by JwtAuthenticationFilter. Not
     * part of the UserDetails contract - it is the whole reason this class
     * exists.
     */
    public Instant getCredentialsChangedAt() {
        return credentialsChangedAt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /** `users.active`. See the class note - this is NOT email verification. */
    @Override
    public boolean isEnabled() {
        return active;
    }

    /* The three flags below are unmodelled in this application and are fixed
     * true, matching what Spring's builder produced before this class existed.
     * Changing one would silently start rejecting logins for a state nothing
     * ever sets. */

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** Never prints the password hash. */
    @Override
    public String toString() {
        return "AuthenticatedUser{username=" + username
                + ", active=" + active
                + ", credentialsChangedAt=" + credentialsChangedAt + "}";
    }
}
