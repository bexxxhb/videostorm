package de.videostorm.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The catalogue listing is always public. What sits beyond it depends on the operating mode:
 * in {@code maintenance} mode the maintenance area is gated behind a login, relying on Spring
 * Security's saved-request flow to land the caller back where they were heading; in the default
 * {@code presentation} (view-only) mode there is no login and everything past the public
 * listing is denied outright, so {@code /maintenance**} and {@code /login} are unreachable even
 * by direct request — defence in depth alongside those routes' controllers not being registered.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${application.operating.mode:presentation}") String operatingMode) throws Exception {
        boolean maintenanceEnabled = "maintenance".equals(operatingMode);

        http.authorizeHttpRequests(authorize -> {
            authorize.requestMatchers("/", "/movies", "/shows", "/movies/*/nfo", "/shows/*/nfo",
                    "/css/**", "/js/**").permitAll();
            if (maintenanceEnabled) {
                authorize.requestMatchers("/login").permitAll()
                        .anyRequest().hasRole("ADMIN");
            } else {
                authorize.anyRequest().denyAll();
            }
        });

        if (maintenanceEnabled) {
            http.formLogin(form -> form
                            .loginPage("/login")
                            .permitAll())
                    .logout(logout -> logout
                            .logoutSuccessUrl("/")
                            .permitAll());
        }

        return http.build();
    }
}
