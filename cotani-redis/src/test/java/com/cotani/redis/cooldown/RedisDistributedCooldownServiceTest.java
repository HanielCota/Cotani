package com.cotani.redis.cooldown;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.UserCooldownTarget;
import com.cotani.redis.CotaniRedis;
import com.cotani.redis.store.RedisKey;
import com.cotani.redis.store.RedisKeyValueStore;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisDistributedCooldownServiceTest {

    private CotaniRedis redis;
    private RedisKeyValueStore store;
    private RedisDistributedCooldownService service;

    @BeforeEach
    void setUp() {
        redis = mock(CotaniRedis.class);
        store = mock(RedisKeyValueStore.class);
        when(redis.store()).thenReturn(store);
        service = new RedisDistributedCooldownService(redis);
    }

    @Test
    void shouldAllowWhenNotOnCooldown() {
        var key = new CooldownKey(new UserCooldownTarget(UUID.randomUUID()), new CooldownAction("pay"));
        when(store.getAsync(any(RedisKey.class))).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(store.setAsync(any(RedisKey.class), any(), any(Duration.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        var result = service.checkAndStartAsync(key, Duration.ofSeconds(10))
                .toCompletableFuture()
                .join();
        assertTrue(result.allowed());
        assertFalse(result.denied());
    }

    @Test
    void shouldDenyWhenOnCooldown() {
        var key = new CooldownKey(new UserCooldownTarget(UUID.randomUUID()), new CooldownAction("pay"));
        long futureMillis = System.currentTimeMillis() + 5000;
        when(store.getAsync(any(RedisKey.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(String.valueOf(futureMillis))));

        var result = service.checkAndStartAsync(key, Duration.ofSeconds(10))
                .toCompletableFuture()
                .join();
        assertTrue(result.denied());
        assertFalse(result.allowed());
        assertTrue(result.remaining().toMillis() > 0);
    }
}
