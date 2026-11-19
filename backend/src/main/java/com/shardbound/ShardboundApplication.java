package com.shardbound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the Shardbound game server: loads and validates the content, applies
 * Flyway migrations and serves the REST API. Run with the {@code dev} profile
 * locally ({@code npm run dev} at the repo root does this).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ShardboundApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShardboundApplication.class, args);
    }
}
