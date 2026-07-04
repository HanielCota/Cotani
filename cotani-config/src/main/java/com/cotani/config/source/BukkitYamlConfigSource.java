package com.cotani.config.source;

import com.cotani.config.exception.ConfigException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

public final class BukkitYamlConfigSource implements ConfigSource {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Plugin plugin;
    private final String resourceName;
    private final Path path;
    private final boolean createMissing;
    private final boolean copyDefaults;
    private volatile boolean defaultsApplied;
    private YamlConfiguration yaml = new YamlConfiguration();

    public BukkitYamlConfigSource(
            Plugin plugin, String resourceName, Path path, boolean createMissing, boolean copyDefaults) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.resourceName = Objects.requireNonNull(resourceName, "resourceName");
        this.path = Objects.requireNonNull(path, "path");
        this.createMissing = createMissing;
        this.copyDefaults = copyDefaults;
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public void load() {
        createFileWhenNeeded();
        loadYaml();
        loadDefaultsWhenNeeded();
    }

    private void loadYaml() {
        var loaded = new YamlConfiguration();
        try {
            loaded.load(path.toFile());
        } catch (java.io.FileNotFoundException ignored) {
            // file was not created (createMissing=false); leave empty config
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new ConfigException("Could not parse config file " + path + ": " + exception.getMessage(), exception);
        }
        lock.writeLock().lock();
        try {
            yaml = loaded;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void save() {
        lock.writeLock().lock();
        try {
            yaml.save(path.toFile());
        } catch (IOException exception) {
            throw new ConfigException("Could not save config file " + path, exception);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean contains(String path) {
        lock.readLock().lock();
        try {
            return yaml.contains(path);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public @Nullable Object get(String path) {
        lock.readLock().lock();
        try {
            return yaml.get(path);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Entry entry(String path) {
        lock.readLock().lock();
        try {
            return new Entry(yaml.get(path), yaml.contains(path));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void set(String path, @Nullable Object value) {
        lock.writeLock().lock();
        try {
            yaml.set(path, value);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean setIfMissing(String path, @Nullable Object value) {
        lock.writeLock().lock();
        try {
            if (yaml.contains(path)) {
                return false;
            }
            yaml.set(path, value);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Set<String> keys(String path) {
        lock.readLock().lock();
        try {
            ConfigurationSection section = sectionAt(path);
            if (section == null) {
                return Set.of();
            }
            return Set.copyOf(section.getKeys(false));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Map<String, Object> section(String path) {
        lock.readLock().lock();
        try {
            ConfigurationSection section = sectionAt(path);
            if (section == null) {
                return Map.of();
            }
            return new LinkedHashMap<>(section.getValues(false));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<?> list(String path) {
        lock.readLock().lock();
        try {
            Object value = yaml.get(path);
            if (value instanceof List<?> list) {
                return List.copyOf(list);
            }
            return List.of();
        } finally {
            lock.readLock().unlock();
        }
    }

    private @Nullable ConfigurationSection sectionAt(String path) {
        if (path == null || path.isBlank()) {
            return yaml;
        }
        return yaml.getConfigurationSection(path);
    }

    private void createFileWhenNeeded() {
        if (Files.exists(path)) {
            return;
        }
        if (!createMissing) {
            return;
        }
        try {
            var parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            copyResourceOrCreateEmpty();
        } catch (IOException exception) {
            throw new ConfigException("Could not create config file " + path, exception);
        }
    }

    private void copyResourceOrCreateEmpty() throws IOException {
        try (InputStream input = plugin.getResource(resourceName)) {
            if (input == null) {
                Files.createFile(path);
                return;
            }
            Files.copy(input, path);
        }
    }

    private void loadDefaultsWhenNeeded() {
        if (!copyDefaults || defaultsApplied) {
            return;
        }

        YamlConfiguration defaults = loadDefaultsResource();
        if (defaults == null) {
            defaultsApplied = true;
            return;
        }

        lock.writeLock().lock();
        try {
            if (defaultsApplied) {
                return;
            }
            var beforeKeys = yaml.getKeys(true).size();
            yaml.setDefaults(defaults);
            yaml.options().copyDefaults(true);
            defaultsApplied = true;
            var addedKeys = yaml.getKeys(true).size() - beforeKeys;
            if (addedKeys > 0 && createMissing) {
                yaml.save(path.toFile());
            }
        } catch (IOException exception) {
            throw new ConfigException("Could not save defaults for " + resourceName, exception);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private @Nullable YamlConfiguration loadDefaultsResource() {
        try (InputStream input = plugin.getResource(resourceName)) {
            if (input == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new ConfigException("Could not load defaults for " + resourceName, exception);
        }
    }
}
