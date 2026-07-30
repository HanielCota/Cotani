package com.cotani.storage.backend;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

public record MySqlCredentials(
        String host, int port, String database, String username, String password, boolean useSsl, PoolSettings pool)
        implements StorageCredentials {

    private static final Pattern HOST_PATTERN = Pattern.compile("[A-Za-z0-9._:-]+");

    public MySqlCredentials {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(pool, "pool");
        if (host.isBlank()) {
            throw new IllegalArgumentException("MySQL host is required.");
        }
        if (!HOST_PATTERN.matcher(host).matches()) {
            throw new IllegalArgumentException("MySQL host contains unsupported characters.");
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
        var encodedHost = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        var encodedDatabase =
                URLEncoder.encode(database, StandardCharsets.UTF_8).replace("+", "%20");
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
            Objects.requireNonNull(connectionTimeout, "connectionTimeout");
            Objects.requireNonNull(idleTimeout, "idleTimeout");
            Objects.requireNonNull(maxLifetime, "maxLifetime");
            if (maximumPoolSize <= 0) {
                throw new IllegalArgumentException("maximumPoolSize must be positive.");
            }

            if (minimumIdle < 0) {
                throw new IllegalArgumentException("minimumIdle must not be negative.");
            }
            if (minimumIdle > maximumPoolSize) {
                throw new IllegalArgumentException("minimumIdle must not exceed maximumPoolSize.");
            }
            requirePositive(connectionTimeout, "connectionTimeout");
            requirePositive(idleTimeout, "idleTimeout");
            requirePositive(maxLifetime, "maxLifetime");
        }

        public static PoolSettings defaults() {
            return DEFAULT;
        }

        private static void requirePositive(Duration value, String name) {
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive.");
            }
            final long milliseconds;
            try {
                milliseconds = value.toMillis();
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException(name + " is too large.", overflow);
            }
            if (milliseconds == 0) {
                throw new IllegalArgumentException(name + " must be at least one millisecond.");
            }
        }
    }
}
