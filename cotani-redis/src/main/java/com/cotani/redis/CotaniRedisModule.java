package com.cotani.redis;

import com.cotani.AsyncCloseable;
import com.cotani.redis.config.RedisConfig;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.bukkit.plugin.Plugin;

/**
 * Lifecycle bridge for registering CotaniRedis with {@code Cotani.forPlugin(plugin)}.
 */
public final class CotaniRedisModule implements AutoCloseable, AsyncCloseable {

    private final CotaniRedis redis;

    private CotaniRedisModule(CotaniRedis redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    public static CotaniRedisModule create(Plugin plugin, RedisConfig config) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        return new CotaniRedisModule(CotaniRedis.create(plugin, config));
    }

    public static CotaniRedisModule create(Plugin plugin, RedisConfig config, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(scheduler, "scheduler");
        return new CotaniRedisModule(CotaniRedis.create(plugin, config, scheduler));
    }

    public static CotaniRedisModule of(CotaniRedis redis) {
        Objects.requireNonNull(redis, "redis");
        return new CotaniRedisModule(redis);
    }

    public CotaniRedis redis() {
        return redis;
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        return redis.closeAsync();
    }

    @Override
    public void close() {
        redis.close();
    }
}
