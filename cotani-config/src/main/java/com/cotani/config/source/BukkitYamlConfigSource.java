package com.cotani.config.source;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.security.ConfigPaths;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

public final class BukkitYamlConfigSource implements ConfigSource {

    private static final long MAX_CONFIG_FILE_BYTES = 4L * 1024L * 1024L;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Plugin plugin;
    private final String resourceName;
    private final Path path;
    private final Path root;
    private final boolean createMissing;
    private final boolean copyDefaults;
    private final AtomicBoolean defaultsApplied = new AtomicBoolean(false);
    private YamlConfiguration yaml = new YamlConfiguration();

    private BukkitYamlConfigSource(
            Plugin plugin, String resourceName, Path path, boolean createMissing, boolean copyDefaults) {
        this(plugin, resourceName, path, parentOf(path), createMissing, copyDefaults);
    }

    public static BukkitYamlConfigSource create(
            Plugin plugin, String resourceName, Path path, boolean createMissing, boolean copyDefaults) {
        return new BukkitYamlConfigSource(plugin, resourceName, path, createMissing, copyDefaults);
    }

    private BukkitYamlConfigSource(
            Plugin plugin, String resourceName, Path path, Path root, boolean createMissing, boolean copyDefaults) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.resourceName = Objects.requireNonNull(resourceName, "resourceName");
        this.path = Objects.requireNonNull(path, "path");
        this.root = Objects.requireNonNull(root, "root");
        this.createMissing = createMissing;
        this.copyDefaults = copyDefaults;
    }

    public static BukkitYamlConfigSource create(
            Plugin plugin, String resourceName, Path path, Path root, boolean createMissing, boolean copyDefaults) {
        return new BukkitYamlConfigSource(plugin, resourceName, path, root, createMissing, copyDefaults);
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
            String content = readContainedFile(path);
            YamlInputLimits.validate(content);
            loaded.loadFromString(content);
        } catch (FileNotFoundException ignored) {
            // file was not created (createMissing=false); leave empty config
        } catch (IOException | InvalidConfigurationException exception) {
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
            saveContainedFile(yaml.saveToString());
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
            return Map.copyOf(section.getValues(false));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Object> list(String path) {
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
        if (path.isBlank()) {
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
            byte[] content = input == null ? new byte[0] : readBounded(input);
            var verified = ConfigPaths.requireContained(path, root);
            try (var channel = Files.newByteChannel(
                    verified,
                    Set.<OpenOption>of(
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))) {
                writeFully(channel, content);
            }
            ConfigPaths.requireContained(path, root);
        }
    }

    private void loadDefaultsWhenNeeded() {
        if (!copyDefaults || defaultsApplied.get()) {
            return;
        }

        YamlConfiguration defaults = loadDefaultsResource();
        if (defaults == null) {
            defaultsApplied.set(true);
            return;
        }

        lock.writeLock().lock();
        try {
            if (defaultsApplied.get()) {
                return;
            }
            var beforeKeys = yaml.getKeys(true).size();
            yaml.setDefaults(defaults);
            yaml.options().copyDefaults(true);
            defaultsApplied.set(true);
            var addedKeys = yaml.getKeys(true).size() - beforeKeys;
            if (addedKeys > 0 && createMissing) {
                saveContainedFile(yaml.saveToString());
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
            String content = new String(readBounded(input), StandardCharsets.UTF_8);
            YamlInputLimits.validate(content);
            var defaults = new YamlConfiguration();
            defaults.loadFromString(content);
            return defaults;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new ConfigException("Could not load defaults for " + resourceName, exception);
        }
    }

    private String readContainedFile(Path candidate) throws IOException {
        var verified = ConfigPaths.requireContained(candidate, root);
        if (!Files.exists(verified, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileNotFoundException(verified.toString());
        }
        var before = Files.readAttributes(verified, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.size() > MAX_CONFIG_FILE_BYTES) {
            throw new ConfigException(
                    "Config file is not regular or exceeds " + MAX_CONFIG_FILE_BYTES + " bytes: " + verified);
        }
        byte[] bytes;
        try (var channel = Files.newByteChannel(
                        verified, Set.<OpenOption>of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
                var input = Channels.newInputStream(channel)) {
            bytes = readBounded(input);
        }
        ConfigPaths.requireContained(candidate, root);
        var after = Files.readAttributes(verified, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!Objects.equals(before.fileKey(), after.fileKey()) || before.size() != after.size()) {
            throw new ConfigException("Config file changed while it was being read: " + verified);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void saveContainedFile(String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONFIG_FILE_BYTES) {
            throw new ConfigException("Serialized config exceeds " + MAX_CONFIG_FILE_BYTES + " bytes: " + path);
        }
        var verified = ConfigPaths.requireContained(path, root);
        var parent = Objects.requireNonNull(verified.getParent(), "config parent");
        ConfigPaths.requireContained(parent, root);
        var temporary = Files.createTempFile(parent, ".cotani-config-", ".tmp");
        try {
            try (var channel = Files.newByteChannel(
                    temporary,
                    Set.<OpenOption>of(
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            LinkOption.NOFOLLOW_LINKS))) {
                writeFully(channel, bytes);
            }
            ConfigPaths.requireContained(path, root);
            try {
                Files.move(temporary, verified, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, verified, StandardCopyOption.REPLACE_EXISTING);
            }
            ConfigPaths.requireContained(path, root);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        var output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_CONFIG_FILE_BYTES) {
                throw new ConfigException("YAML input exceeds maximum size " + MAX_CONFIG_FILE_BYTES + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static Path parentOf(Path path) {
        return Objects.requireNonNull(
                Objects.requireNonNull(path, "path").toAbsolutePath().getParent(), "path parent");
    }

    private static void writeFully(SeekableByteChannel channel, byte[] content) throws IOException {
        var buffer = ByteBuffer.wrap(content);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
}
