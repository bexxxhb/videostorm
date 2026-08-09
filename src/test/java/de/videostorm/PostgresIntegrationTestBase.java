package de.videostorm;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real database.
 *
 * <p>The container starts once in a static initialiser and is deliberately never stopped, so
 * every subclass shares one PostgreSQL instance for the whole test run; Testcontainers' Ryuk
 * sidecar removes it when the JVM exits. The {@code @Testcontainers} extension is not used
 * because it stops the container after each test class, paying the startup cost again.
 *
 * <p>Also supplies the admin credentials that {@code SecurityConfig} requires at startup, so
 * every full-context test boots without having to know about that requirement.
 */
@TestPropertySource(properties = {
        "videostorm.admin.username=" + PostgresIntegrationTestBase.ADMIN_USERNAME,
        "videostorm.admin.password=" + PostgresIntegrationTestBase.ADMIN_PASSWORD
})
public abstract class PostgresIntegrationTestBase {

    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "test-password";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
