package com.shardbound.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for integration tests: the full application on a random port,
 * backed by the shared Testcontainers PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = SharedPostgres.POSTGRES;

    @LocalServerPort
    private int port;

    protected TestApi api;

    @BeforeEach
    void connectApi() {
        api = new TestApi("http://localhost:" + port);
    }
}
