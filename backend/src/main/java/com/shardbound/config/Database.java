package com.shardbound.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Hands out JDBC connections. A new connection per unit of work keeps the
 * code simple and is fast enough for one local player (HikariCP is optional
 * per spec §2 and not used). Callers close connections with try-with-resources.
 */
public class Database {

    private final String url;
    private final String user;
    private final String password;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static Database from(AppConfig config) {
        return new Database(config.dbUrl(), config.dbUser(), config.dbPassword());
    }

    /** Opens a new connection (auto-commit on, as JDBC defaults). */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
