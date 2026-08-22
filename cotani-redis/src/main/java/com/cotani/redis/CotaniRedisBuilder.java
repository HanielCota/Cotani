package com.cotani.redis;

import com.cotani.redis.config.RedisConfig;
import com.cotani.redis.internal.DefaultCotaniRedis;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Fluent builder for creating {@link CotaniRedis} instances.
 */
public final class CotaniRedisBuilder {

    private @Nullable Plugin plugin;
    private RedisConfig config = RedisConfig.localhost();
    private @Nullable PaperTaskScheduler scheduler;

    public CotaniRedisBuilder() {
        // Default constructor for fluent builder instantiation
    }

    public CotaniRedisBuilder plugin(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        return this;
    }

    public CotaniRedisBuilder config(RedisConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        return this;
    }

    public CotaniRedisBuilder scheduler(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        return this;
    }

    public CotaniRedis build() {
        var targetPlugin = Objects.requireNonNull(plugin, "Plugin must be configured on CotaniRedisBuilder");
        return new DefaultCotaniRedis(targetPlugin, config, scheduler);
    }
}
