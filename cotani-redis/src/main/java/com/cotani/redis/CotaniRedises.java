package com.cotani.redis;

import com.cotani.redis.config.RedisConfig;
import com.cotani.task.api.PaperTaskScheduler;
import org.bukkit.plugin.Plugin;

/**
 * Entrypoint factory for the {@code cotani-redis} module.
 */
public final class CotaniRedises {

    private CotaniRedises() {}

    /**
     * Creates a new fluent builder for configuring and instantiating {@link CotaniRedis}.
     *
     * @return new builder
     */
    public static CotaniRedisBuilder builder() {
        return CotaniRedis.builder();
    }

    /**
     * Creates a {@link CotaniRedis} instance for a plugin using the given configuration.
     *
     * @param plugin owning plugin
     * @param config redis configuration
     * @return new CotaniRedis instance
     */
    public static CotaniRedis create(Plugin plugin, RedisConfig config) {
        return CotaniRedis.create(plugin, config);
    }

    /**
     * Creates a {@link CotaniRedis} instance for a plugin using the given configuration and scheduler.
     *
     * @param plugin owning plugin
     * @param config redis configuration
     * @param scheduler task scheduler
     * @return new CotaniRedis instance
     */
    public static CotaniRedis create(Plugin plugin, RedisConfig config, PaperTaskScheduler scheduler) {
        return CotaniRedis.create(plugin, config, scheduler);
    }
}
