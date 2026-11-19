package com.shardbound.support;

import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL 17 container for the whole test run (P-08, P-21). Started on
 * first use and stopped by Testcontainers when the JVM exits, so every
 * integration test and every Spring context shares it.
 */
public final class SharedPostgres {

    public static final PostgreSQLContainer POSTGRES = start();

    private SharedPostgres() {
    }

    private static PostgreSQLContainer start() {
        PostgreSQLContainer container = new PostgreSQLContainer("postgres:17-alpine");
        container.start();
        return container;
    }
}
