package com.cotani.config;

import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.task.api.TaskChain;
import java.util.Collection;
import org.bukkit.plugin.Plugin;

public interface CotaniConfigs extends AutoCloseable {

    static CotaniConfigsBuilder create(Plugin plugin) {
        return new CotaniConfigsBuilder(plugin);
    }

    static CotaniConfigsBuilder create(Plugin plugin, com.cotani.task.api.PaperTaskScheduler scheduler) {
        return new CotaniConfigsBuilder(plugin).scheduler(scheduler);
    }

    CotaniConfig file(String name);

    Collection<CotaniConfig> files();

    ConfigSerializerRegistry serializers();

    void reload();

    TaskChain<Void> reloadAsync();

    void save();

    @Override
    void close();
}
