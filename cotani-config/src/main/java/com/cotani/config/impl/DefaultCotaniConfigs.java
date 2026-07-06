package com.cotani.config.impl;

import com.cotani.config.CotaniConfig;
import com.cotani.config.CotaniConfigs;
import com.cotani.config.binder.ConfigBinder;
import com.cotani.config.binder.RecordConfigBinder;
import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.source.BukkitYamlConfigSource;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;
import com.cotani.task.util.VoidResult;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;

public final class DefaultCotaniConfigs implements CotaniConfigs {

    private final Plugin plugin;
    private final Path folder;
    private final PaperTaskScheduler scheduler;
    private final ConfigSerializerRegistry serializers;
    private final ConfigBinder binder;
    private final boolean createMissingFiles;
    private final boolean copyDefaults;
    private final Map<String, CotaniConfig> files = new ConcurrentHashMap<>();

    public DefaultCotaniConfigs(
            Plugin plugin,
            Path folder,
            PaperTaskScheduler scheduler,
            ConfigSerializerRegistry serializers,
            boolean createMissingFiles,
            boolean copyDefaults) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.folder = Objects.requireNonNull(folder, "folder");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
        this.binder = new RecordConfigBinder(serializers);
        this.createMissingFiles = createMissingFiles;
        this.copyDefaults = copyDefaults;
    }

    public void register(String name) {
        var resolved = folder.resolve(name).normalize();
        if (!resolved.startsWith(folder.normalize())) {
            throw new ConfigException("Config path escapes base folder: " + name);
        }
        var source = new BukkitYamlConfigSource(plugin, name, resolved, createMissingFiles, copyDefaults);
        files.put(name, new DefaultCotaniConfig(name, source, serializers, binder));
    }

    @Override
    public CotaniConfig file(String name) {
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
        return scheduler.supplyAsync(() -> {
            reload();
            return VoidResult.nullValue();
        });
    }

    @Override
    public void save() {
        files.values().forEach(CotaniConfig::save);
    }

    @Override
    public void close() {
        files.clear();
    }
}
