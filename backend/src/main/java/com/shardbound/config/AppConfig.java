package com.shardbound.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Settings read from {@code db.properties} (gitignored; copy it from
 * db.properties.example). Looked up first on the classpath
 * (src/main/resources), then as a file path.
 */
public record AppConfig(String dbUrl, String dbUser, String dbPassword,
                        String testDbUrl, String testDbUser, String testDbPassword,
                        int port, boolean simulateSaveCrash, File contentDir) {

    public static final String DEFAULT_RESOURCE = "db.properties";

    /** Loads db.properties; throws IllegalStateException with setup help if it is missing. */
    public static AppConfig load() {
        Properties props = new Properties();
        try (InputStream in = open()) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read db.properties: " + e.getMessage(), e);
        }
        return fromProperties(props);
    }

    /** Builds a config from already-loaded properties (also used by tests). */
    public static AppConfig fromProperties(Properties props) {
        return new AppConfig(
                required(props, "db.url"),
                required(props, "db.user"),
                props.getProperty("db.password", ""),
                props.getProperty("test.db.url", ""),
                props.getProperty("test.db.user", ""),
                props.getProperty("test.db.password", ""),
                Integer.parseInt(props.getProperty("app.port", "7070")),
                Boolean.parseBoolean(props.getProperty("app.simulateSaveCrash", "false")),
                new File(props.getProperty("app.contentDir", defaultContentDir())));
    }

    /** The same settings pointing at the integration-test database (S6). */
    public AppConfig forTests() {
        return new AppConfig(testDbUrl, testDbUser, testDbPassword, testDbUrl, testDbUser, testDbPassword,
                port, simulateSaveCrash, contentDir);
    }

    /** Copy with the crash-simulation switch changed (demo and SaveRepositoryIT). */
    public AppConfig withSimulateSaveCrash(boolean value) {
        return new AppConfig(dbUrl, dbUser, dbPassword, testDbUrl, testDbUser, testDbPassword,
                port, value, contentDir);
    }

    private static InputStream open() throws IOException {
        InputStream fromClasspath = AppConfig.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        File file = new File("src/main/resources/" + DEFAULT_RESOURCE);
        if (file.exists()) {
            return new FileInputStream(file);
        }
        throw new IOException("db.properties not found. Copy backend/src/main/resources/db.properties.example "
                + "to db.properties and fill in your PostgreSQL password.");
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("db.properties is missing '" + key + "'");
        }
        return value;
    }

    /** Commands run from backend/, so content/ is one level up. */
    private static String defaultContentDir() {
        File sibling = new File("../content");
        return sibling.isDirectory() ? sibling.getPath() : "content";
    }
}
