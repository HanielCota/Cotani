package com.cotani.redis.internal;

import com.cotani.api.InternalApi;
import com.cotani.redis.ratelimit.RateLimitResult;
import com.cotani.redis.ratelimit.RedisRateLimiter;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

@InternalApi
public final class DefaultRedisRateLimiter implements RedisRateLimiter {

    private static final String RATE_LIMIT_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowMillis = tonumber(ARGV[2])
            local maxRequests = tonumber(ARGV[3])
            local tokens = tonumber(ARGV[4])
            local nonce = ARGV[5]
            local clearBefore = now - windowMillis
            redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)
            local currentCount = redis.call('ZCARD', key)
            if (currentCount + tokens) <= maxRequests then
                for i = 1, tokens do
                    redis.call('ZADD', key, now, nonce .. ':' .. i)
                end
                redis.call('PEXPIRE', key, windowMillis)
                return { 1, currentCount + tokens, maxRequests, windowMillis }
            end
            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            local resetAfter = windowMillis
            if oldest and #oldest >= 2 then
                local oldestTime = tonumber(oldest[2])
                resetAfter = math.max(0, (oldestTime + windowMillis) - now)
            end
            return { 0, currentCount, maxRequests, resetAfter }
            """;

    private final Supplier<StatefulRedisConnection<String, String>> stringConnectionSupplier;

    public DefaultRedisRateLimiter(Supplier<StatefulRedisConnection<String, String>> stringConnectionSupplier) {
        this.stringConnectionSupplier = Objects.requireNonNull(stringConnectionSupplier, "stringConnectionSupplier");
    }

    private RedisAsyncCommands<String, String> requireCommands() {
        var connection = stringConnectionSupplier.get();
        if (connection == null) {
            throw new IllegalStateException("Redis connection is not active");
        }
        return connection.async();
    }

    @Override
    public CompletionStage<RateLimitResult> tryAcquireAsync(String rateLimitKey, int maxRequests, Duration window) {
        return tryAcquireAsync(rateLimitKey, 1, maxRequests, window);
    }

    @Override
    public CompletionStage<RateLimitResult> tryAcquireAsync(
            String rateLimitKey, int tokens, int maxRequests, Duration window) {
        Objects.requireNonNull(rateLimitKey, "rateLimitKey");
        Objects.requireNonNull(window, "window");
        if (rateLimitKey.isBlank()) {
            throw new IllegalArgumentException("rateLimitKey must not be blank");
        }
        if (tokens <= 0) {
            throw new IllegalArgumentException("tokens must be positive");
        }
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }

        String rawKey = "ratelimit:" + rateLimitKey;
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        String nonce = java.util.UUID.randomUUID().toString();

        String[] keys = new String[] {rawKey};
        String[] values = new String[] {
            String.valueOf(now),
            String.valueOf(windowMillis),
            String.valueOf(maxRequests),
            String.valueOf(tokens),
            nonce
        };

        return requireCommands()
                .<List<Long>>eval(RATE_LIMIT_LUA, ScriptOutputType.MULTI, keys, values)
                .thenApply(res -> {
                    if (res == null || res.size() < 4) {
                        return new RateLimitResult(false, maxRequests, maxRequests, window);
                    }
                    boolean allowed = res.get(0) != null && res.get(0) == 1L;
                    long currentCount = res.get(1) != null ? res.get(1) : 0L;
                    long max = res.get(2) != null ? res.get(2) : maxRequests;
                    long resetMillis = res.get(3) != null ? res.get(3) : windowMillis;
                    return new RateLimitResult(allowed, currentCount, max, Duration.ofMillis(resetMillis));
                });
    }

    @Override
    public CompletionStage<Void> resetAsync(String rateLimitKey) {
        Objects.requireNonNull(rateLimitKey, "rateLimitKey");
        String rawKey = "ratelimit:" + rateLimitKey;
        return requireCommands().del(rawKey).thenApply(_ -> (Void) null);
    }
}
