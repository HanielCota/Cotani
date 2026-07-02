package com.cotani.config.section;

import com.cotani.config.binder.ConfigBinder;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.source.ConfigSource;
import com.cotani.config.value.ConfigValue;
import java.util.Set;

public final class ConfigSection {

    private final String file;
    private final String path;
    private final ConfigSource source;
    private final ConfigSerializerRegistry serializers;
    private final ConfigBinder binder;

    public ConfigSection(
            String file, String path, ConfigSource source, ConfigSerializerRegistry serializers, ConfigBinder binder) {
        this.file = file;
        this.path = path;
        this.source = source;
        this.serializers = serializers;
        this.binder = binder;
    }

    public String file() {
        return file;
    }

    public String path() {
        return path;
    }

    public boolean contains(String childPath) {
        return source.contains(join(childPath));
    }

    public Set<String> keys() {
        return source.keys(path);
    }

    public ConfigValue value(String childPath) {
        var fullPath = join(childPath);
        var entry = source.entry(fullPath);
        return new ConfigValue(file, fullPath, entry.raw(), entry.exists(), serializers);
    }

    public ConfigSection section(String childPath) {
        return new ConfigSection(file, join(childPath), source, serializers, binder);
    }

    public <T> T bind(Class<T> type) {
        return binder.bind(this, type);
    }

    private String join(String childPath) {
        if (path == null || path.isBlank()) {
            return childPath;
        }
        if (childPath == null || childPath.isBlank()) {
            return path;
        }
        return path + "." + childPath;
    }
}
