package de.videostorm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application against a real PostgreSQL instance, proving the compose-time
 * wiring — datasource, Flyway, JPA — holds together outside of any web slice.
 */
@SpringBootTest
class ApplicationBootstrapIT extends PostgresIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesTheBaselineMigration() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success",
                Integer.class);

        assertThat(applied).isOne();
    }

    @Test
    void theDatabaseIsReachable() {
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isOne();
    }
}
