package com.cotani.config.internal;

import com.cotani.api.InternalApi;
import com.cotani.config.CotaniConfig;
import com.cotani.config.CotaniConfigs;
import com.cotani.config.binder.ConfigBinder;
import com.cotani.config.binder.RecordConfigBinder;
import com.cotani.config.exception.ConfigException;
import com.cotani.config.security.ConfigPaths;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.source.BukkitYamlConfigSource;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;
import com.cotani.task.api.TaskChainFactory;
import com.cotani.task.util.VoidResult;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

@InternalApi
public final class DefaultCotaniConfigs implements CotaniConfigs {
    private final Plugin plugin;
    private final Path folder;
    private final TaskChainFactory chainFactory;
    private final ConfigSerializerRegistry serializers;
    private final ConfigBinder binder;
    private final boolean createMissingFiles;
    private final boolean copyDefaults;
    private final Map<String, CotaniConfig> files = new ConcurrentHashMap<>();

    private DefaultCotaniConfigs(
            Plugin plugin,
            Path folder,
            PaperTaskScheduler scheduler,
            ConfigSerializerRegistry serializers,
            boolean createMissingFiles,
            boolean copyDefaults) {
        this(plugin, folder, (TaskChainFactory) scheduler, serializers, createMissingFiles, copyDefaults);
    }

    public static DefaultCotaniConfigs create(
            Plugin plugin,
            Path folder,
            PaperTaskScheduler scheduler,
            ConfigSerializerRegistry serializers,
            boolean createMissingFiles,
            boolean copyDefaults) {
        return new DefaultCotaniConfigs(plugin, folder, scheduler, serializers, createMissingFiles, copyDefaults);
    }

    private DefaultCotaniConfigs(
            Plugin plugin,
            Path folder,
            TaskChainFactory chainFactory,
            ConfigSerializerRegistry serializers,
            boolean createMissingFiles,
            boolean copyDefaults) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.folder = Objects.requireNonNull(folder, "folder").toAbsolutePath().normalize();
        this.chainFactory = Objects.requireNonNull(chainFactory, "chainFactory");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
        this.binder = RecordConfigBinder.create(serializers);
        this.createMissingFiles = createMissingFiles;
        this.copyDefaults = copyDefaults;
    }

    public static DefaultCotaniConfigs create(
            Plugin plugin,
            Path folder,
            TaskChainFactory chainFactory,
            ConfigSerializerRegistry serializers,
            boolean createMissingFiles,
            boolean copyDefaults) {
        return new DefaultCotaniConfigs(plugin, folder, chainFactory, serializers, createMissingFiles, copyDefaults);
    }

    public void register(String name) {
        Objects.requireNonNull(name, "name");

        var resolved = ConfigPaths.requireContained(folder.resolve(name), folder);
        var source = BukkitYamlConfigSource.create(plugin, name, resolved, folder, createMissingFiles, copyDefaults);
        files.put(name, DefaultCotaniConfig.create(name, source, serializers, binder, chainFactory));
    }

    @Override
    public CotaniConfig file(String name) {
        Objects.requireNonNull(name, "name");
        var config = files.get(name);

        if (config != null) {
            return config;
        }

        throw new ConfigException("Config file not registered: " + name);
    }

    @Override
    public Collection<CotaniConfig> files() {
        return ListCopy.copy(files.values());
    }

    @Override
    public ConfigSerializerRegistry serializers() {
        return serializers;
    }

    @Override
    public void reload() {
        requireNonPrimaryThread("reloadAsync()");
        reloadFiles();
    }

    private void reloadFiles() {
        ConfigException firstFailure = null;

        for (var config : files.values()) {
            try {
                config.reload();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, exception, () -> "Could not reload config " + config.name());
                if (firstFailure == null) {
                    firstFailure = new ConfigException("Could not reload config " + config.name(), exception);
                    continue;
                }
                firstFailure.addSuppressed(exception);
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    @Override
    public TaskChain<Void> reloadAsync() {
        return chainFactory.supplyAsync(() -> {
            reloadFiles();
            return VoidResult.nullValue();
        });
    }

    @Override
    public void save() {
        requireNonPrimaryThread("saveAsync()");
        saveFiles();
    }

    @Override
    public TaskChain<Void> saveAsync() {
        return chainFactory.supplyAsync(() -> {
            saveFiles();
            return VoidResult.nullValue();
        });
    }

    private void saveFiles() {
        files.values().forEach(CotaniConfig::save);
    }

    @Override
    public void close() {
        files.clear();
    }

    private static void requireNonPrimaryThread(String alternative) {
        if (Bukkit.getServer() != null && Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "Synchronous config file I/O is not allowed on the Paper primary thread; use "
                            + alternative
                            + " instead.");
        }
    }
}
