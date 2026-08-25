package com.cotani.config.binder;

import com.cotani.config.source.ConfigSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
final class InMemoryConfigSource implements ConfigSource {
    private final Path path;
    private final String rootPath;
    private final Map<String, Object> values;

    InMemoryConfigSource(String file, String rootPath, Map<String, Object> values) {
        this.path = Path.of(Objects.requireNonNull(file, "file"));
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath");
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values, "values")));
    }

    @Override
    public Path path() {
        return path;
    }

    @Override
    public void load() {}

    @Override
    public void save() {}

    @Override
    public boolean contains(String path) {
        return resolve(path).exists();
    }

    @Override
    public @Nullable Object get(String path) {
        return resolve(path).raw();
    }

    @Override
    public Entry entry(String path) {
        var resolved = resolve(path);
        return new Entry(resolved.raw(), resolved.exists());
    }

    @Override
    public void set(String path, @Nullable Object value) {
        throw new UnsupportedOperationException("In-memory config sections are read-only");
    }

    @Override
    public boolean setIfMissing(String path, @Nullable Object value) {
        throw new UnsupportedOperationException("In-memory config sections are read-only");
    }

    @Override
    public Set<String> keys(String path) {
        Object value = resolve(path).raw();
        if (!(value instanceof Map<?, ?> map)) {
            return Set.of();
        }
        return map.keySet().stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Map<String, Object> section(String path) {
        Object value = resolve(path).raw();
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }

        Map<String, Object> sectionValues = new LinkedHashMap<>();
        map.forEach((key, sectionValue) -> sectionValues.put(String.valueOf(key), sectionValue));
        return Collections.unmodifiableMap(sectionValues);
    }

    @Override
    public List<Object> list(String path) {
        Object value = resolve(path).raw();
        return value instanceof List<?> list ? List.copyOf(list) : List.of();
    }

    private Entry resolve(String requestedPath) {
        var relativePath = relativePath(requestedPath);
        if (relativePath.isBlank()) {
            return new Entry(values, true);
        }

        Object current = values;
        for (var part : parts(relativePath)) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) {
                return new Entry(null, false);
            }
            current = map.get(part);
        }
        return new Entry(current, true);
    }

    private String relativePath(String requestedPath) {
        if (requestedPath.isBlank() || requestedPath.equals(rootPath)) {
            return "";
        }
        var prefix = rootPath + ".";
        return requestedPath.startsWith(prefix) ? requestedPath.substring(prefix.length()) : requestedPath;
    }

    private List<String> parts(String path) {
        List<String> parts = new ArrayList<>();
        var start = 0;
        for (var index = 0; index < path.length(); index++) {
            if (path.charAt(index) == '.') {
                parts.add(path.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(path.substring(start));
        return parts;
    }
}
