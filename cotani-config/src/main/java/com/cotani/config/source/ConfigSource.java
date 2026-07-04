package com.cotani.config.source;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

@SuppressWarnings("NullableProblems")
public interface ConfigSource {

    Path path();

    void load();

    void save();

    boolean contains(String path);

    @Nullable
    Object get(String path);

    Entry entry(String path);

    void set(String path, @Nullable Object value);

    boolean setIfMissing(String path, @Nullable Object value);

    Set<String> keys(String path);

    Map<String, Object> section(String path);

    List<?> list(String path);

    record Entry(@Nullable Object raw, boolean exists) {}
}
