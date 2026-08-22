package com.cotani.redis.cooldown;

import com.cotani.cooldown.api.CooldownEntry;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownResult;
import com.cotani.cooldown.api.DistributedCooldownService;
import com.cotani.cooldown.api.GlobalCooldownTarget;
import com.cotani.cooldown.api.ResourceCooldownTarget;
import com.cotani.cooldown.api.UserCooldownTarget;
import com.cotani.redis.CotaniRedis;
import com.cotani.redis.store.RedisKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * High-performance distributed cooldown service backed by Redis.
 *
 * <p>Enables instant cross-server cooldown synchronization with atomic TTL checks.
 */
public final class RedisDistributedCooldownService implements DistributedCooldownService {

    private static final String DEFAULT_PREFIX = "cooldown:";
    private static final String KEY_PARAM = "key";
    private static final String DURATION_PARAM = "duration";

    private final CotaniRedis redis;
    private final Clock clock;
    private final String keyPrefix;

    public RedisDistributedCooldownService(CotaniRedis redis) {
        this(redis, Clock.systemUTC(), DEFAULT_PREFIX);
    }

    public RedisDistributedCooldownService(CotaniRedis redis, Clock clock, String keyPrefix) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
    }

    @Override
    public CompletionStage<CooldownResult> checkAndStartAsync(CooldownKey key, Duration duration) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(duration, DURATION_PARAM);

        long now = clock.millis();
        long durationMillis = duration.toMillis();
        String rawKey = keyPrefix + formatKey(key);

        var redisKey = RedisKey.of(rawKey);
        long newExpiresMillis = now + durationMillis;

        return redis.store()
                .setIfAbsentAsync(redisKey, String.valueOf(newExpiresMillis), duration)
                .thenCompose(acquired -> {
                    if (Boolean.TRUE.equals(acquired)) {
                        return CompletableFuture.completedFuture(CooldownResult.allowed(key));
                    }
                    return findAsync(key)
                            .thenApply(
                                    opt -> opt.map(e -> CooldownResult.denied(key, e.remaining(clock), e.expiresAt()))
                                            .orElseGet(() -> CooldownResult.denied(
                                                    key, duration, Instant.ofEpochMilli(newExpiresMillis))));
                });
    }

    @Override
    public CompletionStage<Optional<CooldownEntry>> findAsync(CooldownKey key) {
        Objects.requireNonNull(key, KEY_PARAM);
        String rawKey = keyPrefix + formatKey(key);
        long now = clock.millis();

        return redis.store().getAsync(RedisKey.of(rawKey)).thenApply(optVal -> {
            if (optVal.isEmpty()) {
                return Optional.empty();
            }
            try {
                long expiresAtMillis = Long.parseLong(optVal.get());
                if (expiresAtMillis <= now) {
                    return Optional.empty();
                }
                Instant expiresAt = Instant.ofEpochMilli(expiresAtMillis);
                Instant startedAt = Instant.ofEpochMilli(Math.min(now, expiresAtMillis - 1));
                return Optional.of(new CooldownEntry(key, startedAt, expiresAt));
            } catch (Exception _) {
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletionStage<Void> removeAsync(CooldownKey key) {
        Objects.requireNonNull(key, KEY_PARAM);
        String rawKey = keyPrefix + formatKey(key);
        return redis.store().deleteAsync(RedisKey.of(rawKey)).thenApply(_ -> null);
    }

    @Override
    public CompletionStage<Void> clearExpiredAsync() {
        // Redis natively evicts expired keys via TTL/PX
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> clearAllAsync() {
        return redis.store().scanKeysAsync(keyPrefix + "*").thenCompose(keys -> {
            if (keys.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            List<CompletableFuture<Boolean>> futures = keys.stream()
                    .map(k -> redis.store().deleteAsync(k).toCompletableFuture())
                    .toList();
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(_ -> null);
        });
    }

    @Override
    public CompletionStage<Long> sizeAsync() {
        return redis.store().scanKeysAsync(keyPrefix + "*").thenApply(keys -> (long) keys.size());
    }

    @Override
    public void close() {
        // Lifecycle owned by CotaniRedis instance
    }

    private static String formatKey(CooldownKey key) {
        String targetPart =
                switch (key.target()) {
                    case UserCooldownTarget u -> "user:" + u.userId();
                    case GlobalCooldownTarget _ -> "global";
                    case ResourceCooldownTarget r -> "resource:" + r.resourceId();
                };
        return targetPart + ":" + key.action().value();
    }
}
