package com.shardbound.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The dev profile serves the API on 8080 because the Vite proxy points there.
 * A PORT variable meant for another process (dev runners set it for Vite)
 * must not move the API; outside dev, PORT still wins as hosts expect.
 */
class DevProfilePortTest {

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }

    private static String serverPort(String profile) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .profiles(profile)
                .properties(Map.of("PORT", "5173"))
                .run("--spring.docker.compose.enabled=false")) {     // no containers for a config check
            return context.getEnvironment().getProperty("server.port");
        }
    }

    @Test
    void devIgnoresAPortVariableMeantForVite() {
        assertEquals("8080", serverPort("dev"));
    }

    @Test
    void otherProfilesFollowThePortVariable() {
        assertEquals("5173", serverPort("prod"));
    }
}
