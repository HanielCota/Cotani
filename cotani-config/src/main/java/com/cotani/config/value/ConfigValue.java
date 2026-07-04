package com.cotani.config.value;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ConfigValue {

    private final String file;
    private final String path;
    private final @Nullable Object raw;
    private final boolean exists;
    private final ConfigSerializerRegistry serializers;

    public ConfigValue(
            String file, String path, @Nullable Object raw, boolean exists, ConfigSerializerRegistry serializers) {
        this.file = Objects.requireNonNull(file, "file");
        this.path = Objects.requireNonNull(path, "path");
        this.raw = raw;
        this.exists = exists;
        this.serializers = Objects.requireNonNull(serializers, "serializers");
    }

    public String file() {
        return file;
    }

    public String path() {
        return path;
    }

    public @Nullable Object raw() {
        return raw;
    }

    public boolean exists() {
        return exists;
    }

    public String location() {
        return file + ":" + path;
    }

    public String asString() {
        requireExists();
        return as(String.class);
    }

    public int asInt() {
        requireExists();
        return as(Integer.class);
    }

    public long asLong() {
        requireExists();
        return as(Long.class);
    }

    public double asDouble() {
        requireExists();
        return as(Double.class);
    }

    public boolean asBoolean() {
        requireExists();
        return as(Boolean.class);
    }

    public Duration asDuration() {
        requireExists();
        return as(Duration.class);
    }

    public <T> T as(Class<T> type) {
        Objects.requireNonNull(type, "type");
        requireExists();
        return serializers.convert(this, type);
    }

    private void requireExists() {
        if (!exists || raw == null) {
            throw new ConfigException("Missing value at " + location());
        }
    }
}
