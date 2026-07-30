package com.cotani.config;

import com.cotani.config.section.ConfigSection;
import com.cotani.config.validation.ValidationResult;
import com.cotani.config.value.ConfigValue;
import com.cotani.task.api.TaskChain;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;

/**
 * Complete facade for one loaded configuration file.
 *
 * <p>In-memory reads, binding and mutation run on the calling thread. Synchronous save/reload
 * methods perform file I/O and reject Paper's primary thread; their asynchronous counterparts use
 * the configured explicit executor. Paths and required values must be non-null. Prefer a
 * capability superinterface when a consumer needs only read, write, bind, validate or reload
 * access.
 */
@SuppressWarnings("MissingOverride") // Keep declarations on the compatibility facade's binary surface.
public interface CotaniConfig
        extends ConfigReader, ConfigWriter, ConfigBinderView, ConfigValidationView, ReloadableConfig {

    String name();

    Path path();

    void reload();

    TaskChain<Void> reloadAsync();

    void save();

    TaskChain<Void> saveAsync();

    boolean contains(String path);

    void set(String path, Object value);

    void setIfMissing(String path, Object value);

    ConfigValue value(String path);

    ConfigSection section(String path);

    Optional<String> optionalString(String path);

    String getString(String path, String fallback);

    int getInt(String path, int fallback);

    long getLong(String path, long fallback);

    double getDouble(String path, double fallback);

    boolean getBoolean(String path, boolean fallback);

    Duration getDuration(String path, Duration fallback);

    Component getComponent(String path, Component fallback);

    <T> T get(String path, Class<T> type, T fallback);

    <T> List<T> getList(String path, Class<T> type);

    <T> T bind(Class<T> type);

    <T> T bind(String path, Class<T> type);

    <T> T bindOrThrow(Class<T> type);

    <T> T bindOrThrow(String path, Class<T> type);

    <T> ValidationResult validate(Class<T> type);

    <T> ValidationResult validate(String path, Class<T> type);
}
