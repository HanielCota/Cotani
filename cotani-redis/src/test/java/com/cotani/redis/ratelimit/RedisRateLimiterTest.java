package com.cotani.redis.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.redis.internal.DefaultRedisRateLimiter;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisRateLimiterTest {

    private StatefulRedisConnection<String, String> connection;
    private RedisAsyncCommands<String, String> commands;
    private DefaultRedisRateLimiter rateLimiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        connection = mock(StatefulRedisConnection.class);
        commands = mock(RedisAsyncCommands.class);
        when(connection.async()).thenReturn(commands);
        rateLimiter = new DefaultRedisRateLimiter(() -> connection);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAllowWhenUnderLimit() {
        var evalFuture = mock(RedisFuture.class);
        when(evalFuture.thenApply(any()))
                .thenReturn(
                        CompletableFuture.completedFuture(new RateLimitResult(true, 1L, 5L, Duration.ofSeconds(10))));
        when(commands.eval(anyString(), eq(ScriptOutputType.MULTI), any(String[].class), any(String[].class)))
                .thenReturn(evalFuture);

        var result = rateLimiter
                .tryAcquireAsync("player:pay:123", 5, Duration.ofSeconds(10))
                .toCompletableFuture()
                .join();

        assertTrue(result.allowed());
        assertEquals(1L, result.currentRequests());
        assertEquals(5L, result.maxRequests());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResetRateLimitKey() {
        var delFuture = mock(RedisFuture.class);
        when(delFuture.thenApply(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(commands.del("ratelimit:player:pay:123")).thenReturn(delFuture);

        rateLimiter.resetAsync("player:pay:123").toCompletableFuture().join();
        verify(commands).del("ratelimit:player:pay:123");
    }
}
