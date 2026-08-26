package com.cotani.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.redis.config.RedisConfig;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class RedisLifecycleTest {

    @Test
    void rejectsStartingAfterClose() {
        var plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("cotani-redis-lifecycle"));
        var redis = CotaniRedis.create(plugin, RedisConfig.localhost());

        redis.closeAsync().toCompletableFuture().join();

        assertEquals(RedisState.CLOSED, redis.state());
        assertThrows(
                CompletionException.class,
                () -> redis.startAsync().toCompletableFuture().join());
    }
}
