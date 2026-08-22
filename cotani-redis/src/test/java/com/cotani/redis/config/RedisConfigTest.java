package com.cotani.redis.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RedisConfigTest {

    @Test
    void shouldBuildDefaultLocalhostConfig() {
        var config = RedisConfig.localhost();

        assertEquals("127.0.0.1", config.host());
        assertEquals(6379, config.port());
        assertNull(config.username());
        assertNull(config.password());
        assertEquals(0, config.database());
        assertFalse(config.ssl());
        assertEquals("CotaniRedis", config.clientName());
        assertEquals(Duration.ofSeconds(3), config.timeout());
        assertEquals(Duration.ofSeconds(5), config.connectTimeout());
    }

    @Test
    void shouldBuildCustomConfig() {
        var config = RedisConfig.builder()
                .host("redis.example.com")
                .port(6380)
                .username("app")
                .password("secret")
                .database(2)
                .ssl(true)
                .clientName("CustomClient")
                .timeout(Duration.ofSeconds(10))
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        assertEquals("redis.example.com", config.host());
        assertEquals(6380, config.port());
        assertEquals("app", config.username());
        assertEquals("secret", config.password());
        assertEquals(2, config.database());
        assertTrue(config.ssl());
        assertEquals("CustomClient", config.clientName());
        assertEquals(Duration.ofSeconds(10), config.timeout());
        assertEquals(Duration.ofSeconds(15), config.connectTimeout());
    }

    @Test
    void shouldRejectInvalidArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RedisConfig.builder().host("").build());
        assertThrows(
                IllegalArgumentException.class,
                () -> RedisConfig.builder().port(0).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> RedisConfig.builder().port(70000).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> RedisConfig.builder().database(-1).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> RedisConfig.builder().timeout(Duration.ZERO).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> RedisConfig.builder()
                        .connectTimeout(Duration.ofMillis(-5))
                        .build());
    }
}
