package com.cotani.redis;

import com.cotani.AsyncCloseable;
import com.cotani.redis.channel.ChannelId;
import com.cotani.redis.channel.RedisChannel;
import com.cotani.redis.codec.RedisCodec;
import com.cotani.redis.config.RedisConfig;
import com.cotani.redis.lock.DistributedLockService;
import com.cotani.redis.store.RedisKeyValueStore;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.bukkit.plugin.Plugin;

/**
 * Primary Cotani Redis interface providing non-blocking asynchronous Pub/Sub channels,
 * distributed mutual exclusion locks, and key-value operations.
 */
public interface CotaniRedis extends AutoCloseable, AsyncCloseable {

    /**
     * Connects to the Redis cluster and prepares connection pools asynchronously.
     *
     * @return stage completing once connected and ready
     */
    CompletionStage<Void> startAsync();

    /**
     * Resolves a strongly typed Pub/Sub channel.
     *
     * @param id channel identifier
     * @param codec message codec
     * @param <T> payload type
     * @return channel handle
     */
    <T> RedisChannel<T> channel(ChannelId id, RedisCodec<T> codec);

    /**
     * Resolves a string-based Pub/Sub channel.
     *
     * @param id channel identifier
     * @return string channel handle
     */
    default RedisChannel<String> channel(ChannelId id) {
        Objects.requireNonNull(id, "id");
        return channel(id, RedisCodec.string());
    }

    /**
     * Resolves an asynchronous cross-server Request-Response (RPC) communication channel.
     *
     * @param id channel identifier
     * @param requestCodec codec for request payload
     * @param responseCodec codec for response payload
     * @param <Q> request type
     * @param <R> response type
     * @return RPC channel handle
     */
    <Q, R> com.cotani.redis.channel.RedisRpcChannel<Q, R> rpcChannel(
            ChannelId id, RedisCodec<Q> requestCodec, RedisCodec<R> responseCodec);

    /**
     * Resolves a string-based asynchronous cross-server Request-Response (RPC) communication channel.
     *
     * @param id channel identifier
     * @return string RPC channel handle
     */
    default com.cotani.redis.channel.RedisRpcChannel<String, String> rpcChannel(ChannelId id) {
        Objects.requireNonNull(id, "id");
        return rpcChannel(id, RedisCodec.string(), RedisCodec.string());
    }

    /**
     * Returns the distributed lock service.
     *
     * @return distributed lock service
     */
    DistributedLockService locks();

    /**
     * Returns the key-value storage and counter operations.
     *
     * @return key-value store
     */
    RedisKeyValueStore store();

    /**
     * Returns the distributed Sorted Set and Leaderboard operations service.
     *
     * @return sorted set store
     */
    com.cotani.redis.leaderboard.RedisSortedSetStore sortedSets();

    /**
     * Returns the distributed rate limiter service.
     *
     * @return rate limiter service
     */
    com.cotani.redis.ratelimit.RedisRateLimiter rateLimiter();

    /**
     * Sends a ping command to verify Redis connectivity and measure latency.
     *
     * @return stage completing with true if pong was received
     */
    CompletionStage<Boolean> pingAsync();

    /**
     * Returns the current connection and lifecycle state.
     *
     * @return current state
     */
    RedisState state();

    /**
     * Asynchronously closes all connections, cancels subscriptions, and releases event loops.
     *
     * @return stage completing once shutdown finishes
     */
    @Override
    CompletionStage<Void> closeAsync();

    /**
     * Begins closing this instance without blocking.
     *
     * <p>Use {@link #closeAsync()} to observe completion and failures.
     */
    @Override
    void close();

    /**
     * Creates a new fluent builder for configuring and instantiating {@link CotaniRedis}.
     *
     * @return new builder
     */
    static CotaniRedisBuilder builder() {
        return new CotaniRedisBuilder();
    }

    /**
     * Creates a {@link CotaniRedis} instance for a plugin using the given configuration.
     *
     * @param plugin owning plugin
     * @param config redis configuration
     * @return new CotaniRedis instance
     */
    static CotaniRedis create(Plugin plugin, RedisConfig config) {
        return builder().plugin(plugin).config(config).build();
    }

    /**
     * Creates a {@link CotaniRedis} instance for a plugin using the given configuration and scheduler.
     *
     * @param plugin owning plugin
     * @param config redis configuration
     * @param scheduler task scheduler
     * @return new CotaniRedis instance
     */
    static CotaniRedis create(Plugin plugin, RedisConfig config, PaperTaskScheduler scheduler) {
        return builder().plugin(plugin).config(config).scheduler(scheduler).build();
    }
}
