package com.cotani.config.impl;

import com.cotani.config.CotaniConfig;
import com.cotani.config.binder.ConfigBinder;
import com.cotani.config.exception.ConfigValidationException;
import com.cotani.config.section.ConfigSection;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.source.ConfigSource;
import com.cotani.config.validation.ValidationResult;
import com.cotani.config.value.ConfigValue;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;

public final class DefaultCotaniConfig implements CotaniConfig {

    private final String name;
    private final ConfigSource source;
    private final ConfigSerializerRegistry serializers;
    private final ConfigBinder binder;

    public DefaultCotaniConfig(
            String name, ConfigSource source, ConfigSerializerRegistry serializers, ConfigBinder binder) {
        this.name = Objects.requireNonNull(name, "name");
        this.source = Objects.requireNonNull(source, "source");
        this.serializers = Objects.requireNonNull(serializers, "serializers");
        this.binder = Objects.requireNonNull(binder, "binder");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Path path() {
        return source.path();
    }

    @Override
    public void reload() {
        source.load();
    }

    @Override
    public void save() {
        source.save();
    }

    @Override
    public boolean contains(String path) {
        return source.contains(path);
    }

    @Override
    public void set(String path, Object value) {
        source.set(path, serializers.serialize(value));
    }

    @Override
    public void setIfMissing(String path, Object value) {
        source.setIfMissing(path, serializers.serialize(value));
    }

    @Override
    public ConfigValue value(String path) {
        var entry = source.entry(path);
        return new ConfigValue(name, path, entry.raw(), entry.exists(), serializers);
    }

    @Override
    public ConfigSection section(String path) {
        return new ConfigSection(name, path, source, serializers, binder);
    }

    @Override
    public Optional<String> optionalString(String path) {
        var entry = source.entry(path);
        if (!entry.exists() || entry.raw() == null) {
            return Optional.empty();
        }
        return Optional.of(new ConfigValue(name, path, entry.raw(), true, serializers).asString());
    }

    @Override
    public String getString(String path, String fallback) {
        return get(path, String.class, fallback);
    }

    @Override
    public int getInt(String path, int fallback) {
        return get(path, Integer.class, fallback);
    }

    @Override
    public long getLong(String path, long fallback) {
        return get(path, Long.class, fallback);
    }

    @Override
    public double getDouble(String path, double fallback) {
        return get(path, Double.class, fallback);
    }

    @Override
    public boolean getBoolean(String path, boolean fallback) {
        return get(path, Boolean.class, fallback);
    }

    @Override
    public Duration getDuration(String path, Duration fallback) {
        return get(path, Duration.class, fallback);
    }

    @Override
    public Component getComponent(String path, Component fallback) {
        return get(path, Component.class, fallback);
    }

    @Override
    public <T> T get(String path, Class<T> type, T fallback) {
        var entry = source.entry(path);
        if (!entry.exists() || entry.raw() == null) {
            return fallback;
        }
        return new ConfigValue(name, path, entry.raw(), true, serializers).as(type);
    }

    @Override
    public <T> List<T> getList(String path, Class<T> type) {
        return source.list(path).stream()
                .map(raw -> new ConfigValue(name, path, raw, true, serializers).as(type))
                .toList();
    }

    @Override
    public <T> T bind(Class<T> type) {
        return bind("", type);
    }

    @Override
    public <T> T bind(String path, Class<T> type) {
        return section(path).bind(type);
    }

    @Override
    public <T> T bindOrThrow(Class<T> type) {
        return bindOrThrow("", type);
    }

    @Override
    public <T> T bindOrThrow(String path, Class<T> type) {
        var result = validate(path, type);
        if (result.hasErrors()) {
            throw new ConfigValidationException(result);
        }
        return bind(path, type);
    }

    @Override
    public <T> ValidationResult validate(Class<T> type) {
        return validate("", type);
    }

    @Override
    public <T> ValidationResult validate(String path, Class<T> type) {
        return binder.validate(section(path), type);
    }
}
