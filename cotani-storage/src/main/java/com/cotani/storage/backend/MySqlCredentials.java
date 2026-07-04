package com.cotani.storage.backend;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public record MySqlCredentials(
        String host, int port, String database, String username, String password, boolean useSsl, PoolSettings pool)
        implements StorageCredentials {

    public MySqlCredentials {
        if (host.isBlank()) {
            throw new IllegalArgumentException("MySQL host is required.");
        }

        if (port <= 0) {
            throw new IllegalArgumentException("MySQL port must be positive.");
        }

        if (database.isBlank()) {
            throw new IllegalArgumentException("MySQL database is required.");
        }

        if (username.isBlank()) {
            throw new IllegalArgumentException("MySQL username is required.");
        }
    }

    public String jdbcUrl() {
        var encodedHost = URLEncoder.encode(host, StandardCharsets.UTF_8);
        var encodedDatabase = URLEncoder.encode(database, StandardCharsets.UTF_8);
        var base = "jdbc:mysql://" + encodedHost + ":" + port + "/" + encodedDatabase;
        var params = "?serverTimezone=UTC&characterEncoding=utf8"
                + "&cachePrepStmts=true&useServerPrepStmts=true"
                + "&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048";
        var ssl = useSsl ? "&useSSL=true&verifyServerCertificate=true" : "&useSSL=false";
        return base + params + ssl;
    }

    public record PoolSettings(
            int maximumPoolSize,
            int minimumIdle,
            Duration connectionTimeout,
            Duration idleTimeout,
            Duration maxLifetime) {

        private static final PoolSettings DEFAULT =
                new PoolSettings(10, 2, Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(30));

        public PoolSettings {
            if (maximumPoolSize <= 0) {
                throw new IllegalArgumentException("maximumPoolSize must be positive.");
            }

            if (minimumIdle < 0) {
                throw new IllegalArgumentException("minimumIdle must not be negative.");
            }
        }

        public static PoolSettings defaults() {
            return DEFAULT;
        }
    }
}
