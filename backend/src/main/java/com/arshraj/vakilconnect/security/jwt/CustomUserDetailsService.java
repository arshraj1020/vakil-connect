package com.arshraj.vakilconnect.security.jwt;

import com.arshraj.vakilconnect.user.entity.User;
import com.arshraj.vakilconnect.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with email: " + email));

        /*
         * AuthenticatedUser rather than Spring's builder, so the principal can
         * carry credentialsChangedAt for the filter's staleness check without a
         * second query - this row already supplied it.
         *
         * SEMANTICS ARE UNCHANGED. The builder previously received
         * `.disabled(!user.isActive())`; AuthenticatedUser takes `active`
         * directly and returns it from isEnabled(). Same meaning, opposite
         * phrasing - which is precisely why it is spelled out here: reading
         * `isActive()` next to a parameter named `active` is much harder to get
         * backwards than a negated `disabled` flag.
         *
         * Username, password and authorities are passed through verbatim.
         */
        return new AuthenticatedUser(
                user.getEmail(),
                user.getPasswordHash(),
                user.isActive(),
                user.getCredentialsChangedAt(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}