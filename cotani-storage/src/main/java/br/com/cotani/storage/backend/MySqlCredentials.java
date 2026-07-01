package br.com.cotani.storage.backend;

import java.time.Duration;
import java.util.Objects;

public record MySqlCredentials(
        String host, int port, String database, String username, String password, boolean useSsl, PoolSettings pool)
        implements StorageCredentials {

    public MySqlCredentials {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("MySQL host is required.");
        }

        if (port <= 0) {
            throw new IllegalArgumentException("MySQL port must be positive.");
        }

        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("MySQL database is required.");
        }

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("MySQL username is required.");
        }

        password = Objects.requireNonNullElse(password, "");
        pool = Objects.requireNonNullElse(pool, PoolSettings.DEFAULT);
    }

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?serverTimezone=UTC&characterEncoding=utf8"
                + "&cachePrepStmts=true&useServerPrepStmts=true"
                + "&prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048"
                + (useSsl ? "&useSSL=true&verifyServerCertificate=true" : "&useSSL=false");
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

            connectionTimeout = Objects.requireNonNullElse(connectionTimeout, Duration.ofSeconds(10));
            idleTimeout = Objects.requireNonNullElse(idleTimeout, Duration.ofMinutes(1));
            maxLifetime = Objects.requireNonNullElse(maxLifetime, Duration.ofMinutes(30));
        }

        public static PoolSettings defaults() {
            return DEFAULT;
        }
    }
}
