package com.cotani.config;

import com.cotani.config.impl.DefaultCotaniConfigs;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

public final class CotaniConfigsBuilder {

    private final Plugin plugin;
    private final List<String> files = new ArrayList<>();
    private Path folder;
    private @Nullable PaperTaskScheduler scheduler;
    private boolean createMissingFiles = true;
    private boolean copyDefaults = true;

    public CotaniConfigsBuilder(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.folder = plugin.getDataFolder().toPath();
    }

    public CotaniConfigsBuilder folder(Path folder) {
        this.folder = Objects.requireNonNull(folder, "folder");
        return this;
    }

    public CotaniConfigsBuilder scheduler(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        return this;
    }

    public CotaniConfigsBuilder file(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("file name must not be null or blank");
        }
        if (files.contains(name)) {
            throw new IllegalArgumentException("Duplicate file name: " + name);
        }
        files.add(name);
        return this;
    }

    public CotaniConfigsBuilder createMissingFiles(boolean createMissingFiles) {
        this.createMissingFiles = createMissingFiles;
        return this;
    }

    public CotaniConfigsBuilder copyDefaults(boolean copyDefaults) {
        this.copyDefaults = copyDefaults;
        return this;
    }

    public CotaniConfigs load() {
        PaperTaskScheduler resolvedScheduler = requireScheduler();
        ConfigSerializerRegistry registry = ConfigSerializerRegistry.defaults(plugin);
        DefaultCotaniConfigs configs =
                new DefaultCotaniConfigs(plugin, folder, resolvedScheduler, registry, createMissingFiles, copyDefaults);
        files.forEach(configs::register);
        configs.reload();
        return configs;
    }

    private PaperTaskScheduler requireScheduler() {
        PaperTaskScheduler resolved = scheduler;
        if (resolved == null) {
            throw new IllegalStateException("No scheduler configured; call scheduler(...) before load().");
        }
        return resolved;
    }
}
