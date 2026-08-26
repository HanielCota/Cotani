package com.cotani.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.redis.config.RedisConfig;
import com.cotani.redis.store.RedisKey;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisIntegrationTest {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Test
    void startsPingsAndPersistsAValueAgainstRedis() {
        var plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("cotani-redis-integration"));
        var config = RedisConfig.builder()
                .host(REDIS.getHost())
                .port(REDIS.getMappedPort(6379))
                .timeout(Duration.ofSeconds(3))
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        var redis = CotaniRedis.create(plugin, config);

        try {
            redis.startAsync().toCompletableFuture().join();

            assertEquals(RedisState.CONNECTED, redis.state());
            assertTrue(redis.pingAsync().toCompletableFuture().join());

            var key = RedisKey.of("cotani:integration:" + UUID.randomUUID());
            redis.store()
                    .setAsync(key, "integration-value", Duration.ofSeconds(30))
                    .toCompletableFuture()
                    .join();

            assertEquals(
                    Optional.of("integration-value"),
                    redis.store().getAsync(key).toCompletableFuture().join());
        } finally {
            redis.closeAsync().toCompletableFuture().join();
        }
    }
}
