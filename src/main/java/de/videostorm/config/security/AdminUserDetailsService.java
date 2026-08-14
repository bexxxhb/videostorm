package de.videostorm.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The single admin account that guards the maintenance area. The username and password are
 * required runtime configuration — missing either fails application startup, naming the
 * property, via Spring's own placeholder resolution. The plaintext password is BCrypted once
 * here and never retained.
 *
 * <p>A fresh {@link UserDetails} is built on every lookup rather than handing out a shared
 * instance: Spring Security erases an authenticated principal's credentials in place after
 * login, which would otherwise null out the encoded password for every login that follows.
 */
@Component
@ConditionalOnProperty(name = "application.operating.mode", havingValue = "maintenance")
public class AdminUserDetailsService implements UserDetailsService {

    private final String username;
    private final String encodedPassword;

    public AdminUserDetailsService(
            @Value("${videostorm.admin.username}") String username,
            @Value("${videostorm.admin.password}") String password,
            PasswordEncoder passwordEncoder) {
        this.username = username;
        this.encodedPassword = passwordEncoder.encode(password);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        if (!this.username.equals(username)) {
            throw new UsernameNotFoundException(username);
        }
        return User.withUsername(this.username)
                .password(encodedPassword)
                .roles("ADMIN")
                .build();
    }
}
