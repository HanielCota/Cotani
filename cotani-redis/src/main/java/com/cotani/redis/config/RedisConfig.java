package com.cotani.redis.config;

import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable configuration for connecting to a Redis standalone or cluster instance.
 *
 * @param host the Redis server hostname or IP address
 * @param port the Redis server port (typically 6379)
 * @param username optional username for Redis 6+ ACL authentication
 * @param password optional password for authentication
 * @param database the logical Redis database index (0-15)
 * @param ssl whether to use TLS/SSL encryption
 * @param clientName connection client name for identification
 * @param timeout command execution timeout
 * @param connectTimeout connection establishment timeout
 */
public record RedisConfig(
        String host,
        int port,
        @Nullable String username,
        @Nullable String password,
        int database,
        boolean ssl,
        String clientName,
        Duration timeout,
        Duration connectTimeout) {

    public RedisConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(clientName, "clientName");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(connectTimeout, "connectTimeout");

        if (host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535, was: " + port);
        }
        if (database < 0) {
            throw new IllegalArgumentException("database index must not be negative, was: " + database);
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero()) {
            throw new IllegalArgumentException("connectTimeout must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RedisConfig localhost() {
        return builder().build();
    }

    public static final class Builder {
        private String host = "127.0.0.1";
        private int port = 6379;
        private @Nullable String username;
        private @Nullable String password;
        private int database = 0;
        private boolean ssl = false;
        private String clientName = "CotaniRedis";
        private Duration timeout = Duration.ofSeconds(3);
        private Duration connectTimeout = Duration.ofSeconds(5);

        public Builder host(String host) {
            this.host = Objects.requireNonNull(host, "host");
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder username(@Nullable String username) {
            this.username = username;
            return this;
        }

        public Builder password(@Nullable String password) {
            this.password = password;
            return this;
        }

        public Builder database(int database) {
            this.database = database;
            return this;
        }

        public Builder ssl(boolean ssl) {
            this.ssl = ssl;
            return this;
        }

        public Builder clientName(String clientName) {
            this.clientName = Objects.requireNonNull(clientName, "clientName");
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout");
            return this;
        }

        public RedisConfig build() {
            return new RedisConfig(host, port, username, password, database, ssl, clientName, timeout, connectTimeout);
        }
    }
}
