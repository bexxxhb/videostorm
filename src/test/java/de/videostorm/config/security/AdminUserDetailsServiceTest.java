package de.videostorm.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Isolated coverage of the admin credential seam: the values are read from runtime
 * configuration, missing values fail application startup by name, and the password is
 * BCrypted once with the plaintext discarded.
 */
class AdminUserDetailsServiceTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(PropertySourcesPlaceholderConfigurer.class, PropertySourcesPlaceholderConfigurer::new)
            .withBean(PasswordEncoder.class, BCryptPasswordEncoder::new)
            .withBean(AdminUserDetailsService.class);

    @Test
    void failsAtStartupNamingTheMissingUsernameProperty() {
        contextRunner.withPropertyValues("videostorm.admin.password=secret")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause().hasMessageContaining("videostorm.admin.username");
                });
    }

    @Test
    void failsAtStartupNamingTheMissingPasswordProperty() {
        contextRunner.withPropertyValues("videostorm.admin.username=admin")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause().hasMessageContaining("videostorm.admin.password");
                });
    }

    @Test
    void bCryptsThePasswordOnceAndNeverExposesThePlaintext() {
        contextRunner.withPropertyValues(
                        "videostorm.admin.username=admin",
                        "videostorm.admin.password=secret")
                .run(context -> {
                    AdminUserDetailsService service = context.getBean(AdminUserDetailsService.class);
                    UserDetails admin = service.loadUserByUsername("admin");

                    assertThat(admin.getPassword()).startsWith("$2a$").isNotEqualTo("secret");
                });
    }

    @Test
    void survivesCredentialErasureSoASecondLookupStillAuthenticates() {
        contextRunner.withPropertyValues(
                        "videostorm.admin.username=admin",
                        "videostorm.admin.password=secret")
                .run(context -> {
                    AdminUserDetailsService service = context.getBean(AdminUserDetailsService.class);
                    PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);

                    UserDetails firstLookup = service.loadUserByUsername("admin");
                    ((CredentialsContainer) firstLookup).eraseCredentials();

                    UserDetails secondLookup = service.loadUserByUsername("admin");

                    assertThat(passwordEncoder.matches("secret", secondLookup.getPassword())).isTrue();
                });
    }

    @Test
    void rejectsAnyUsernameOtherThanTheConfiguredAdmin() {
        contextRunner.withPropertyValues(
                        "videostorm.admin.username=admin",
                        "videostorm.admin.password=secret")
                .run(context -> {
                    AdminUserDetailsService service = context.getBean(AdminUserDetailsService.class);

                    assertThat(catchThrowable(() -> service.loadUserByUsername("someone-else")))
                            .isInstanceOf(UsernameNotFoundException.class);
                });
    }
}
