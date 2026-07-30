package com.cotani.config;

import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;
import java.util.Collection;
import org.bukkit.plugin.Plugin;

public interface CotaniConfigs extends AutoCloseable {

    static CotaniConfigsBuilder builder(Plugin plugin) {
        return CotaniConfigsBuilder.create(plugin);
    }

    static CotaniConfigsBuilder builder(Plugin plugin, PaperTaskScheduler scheduler) {
        return CotaniConfigsBuilder.create(plugin).scheduler(scheduler);
    }

    CotaniConfig file(String name);

    Collection<CotaniConfig> files();

    ConfigSerializerRegistry serializers();

    void reload();

    TaskChain<Void> reloadAsync();

    /**
     * Saves all registered configuration files synchronously.
     *
     * <p>This compatibility method performs file I/O and therefore rejects calls from the Paper
     * primary thread. Prefer {@link #saveAsync()} in application code.
     */
    void save();

    /** Saves all registered configuration files on the configured asynchronous executor. */
    TaskChain<Void> saveAsync();

    @Override
    void close();
}
