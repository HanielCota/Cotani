package com.cotani.config;

import com.cotani.config.internal.DefaultCotaniConfigs;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

public final class CotaniConfigsBuilder {
    private final Plugin plugin;
    private final List<String> files = new ArrayList<>();
    private Path folder;
    private @Nullable PaperTaskScheduler scheduler;
    private boolean createMissingFiles = true;
    private boolean copyDefaults = true;

    private CotaniConfigsBuilder(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.folder = plugin.getDataFolder().toPath();
    }

    public static CotaniConfigsBuilder create(Plugin plugin) {
        return new CotaniConfigsBuilder(plugin);
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
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("file name must not be blank");
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

    /**
     * Loads and reloads the registered configs synchronously.
     *
     * <p>The configs are guaranteed to be loaded when this method returns, but the caller thread is
     * blocked during file I/O. Prefer {@link #loadAsync()} for non-blocking bootstrap.
     */
    public CotaniConfigs load() {
        requireNonPrimaryThread();
        PaperTaskScheduler resolvedScheduler = requireScheduler();
        ConfigSerializerRegistry registry = ConfigSerializerRegistry.defaults(plugin);
        DefaultCotaniConfigs configs = DefaultCotaniConfigs.create(
                plugin, folder, resolvedScheduler, registry, createMissingFiles, copyDefaults);
        files.forEach(configs::register);
        configs.reload();

        return configs;
    }

    /**
     * Loads and reloads the registered configs asynchronously.
     *
     * <p>The returned stage completes when all config files have been loaded and bound.
     */
    public CompletionStage<CotaniConfigs> loadAsync() {
        PaperTaskScheduler resolvedScheduler = requireScheduler();
        ConfigSerializerRegistry registry = ConfigSerializerRegistry.defaults(plugin);
        DefaultCotaniConfigs configs = DefaultCotaniConfigs.create(
                plugin, folder, resolvedScheduler, registry, createMissingFiles, copyDefaults);
        files.forEach(configs::register);

        return configs.reloadAsync().toCompletionStage().thenApply(_ -> configs);
    }

    private PaperTaskScheduler requireScheduler() {
        PaperTaskScheduler resolved = scheduler;

        if (resolved == null) {
            throw new IllegalStateException("No scheduler configured; call scheduler(...) before load().");
        }

        return resolved;
    }

    private static void requireNonPrimaryThread() {
        if (Bukkit.getServer() != null && Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "Synchronous config file I/O is not allowed on the Paper primary thread; use loadAsync() instead.");
        }
    }
}
