package com.example.voicenotes;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real PostgreSQL in Docker for tests. @ServiceConnection makes Spring Boot point
 * the DataSource at it, so no URL/port is hard-coded and the docker-compose DB isn't needed.
 * Same major version as compose.yaml, so tests see the same SQL behaviour as dev.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
