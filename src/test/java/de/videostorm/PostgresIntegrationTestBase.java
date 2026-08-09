package de.videostorm;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real database.
 *
 * <p>The container starts once in a static initialiser and is deliberately never stopped, so
 * every subclass shares one PostgreSQL instance for the whole test run; Testcontainers' Ryuk
 * sidecar removes it when the JVM exits. The {@code @Testcontainers} extension is not used
 * because it stops the container after each test class, paying the startup cost again.
 */
public abstract class PostgresIntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
