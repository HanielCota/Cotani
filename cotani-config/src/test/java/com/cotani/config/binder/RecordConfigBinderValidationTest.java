package com.cotani.config.binder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.config.annotation.Default;
import com.cotani.config.annotation.Range;
import com.cotani.config.annotation.Required;
import com.cotani.config.section.ConfigSection;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.serializer.defaults.IntegerSerializer;
import com.cotani.config.serializer.defaults.StringSerializer;
import com.cotani.config.source.ConfigSource;
import java.nio.file.Path;
import java.util.*;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RecordConfigBinderValidationTest {

    private final ConfigSerializerRegistry serializers = serializers();
    private final RecordConfigBinder binder = new RecordConfigBinder(serializers);

    private static ConfigSerializerRegistry serializers() {
        var registry = new ConfigSerializerRegistry();
        registry.register(new IntegerSerializer());
        registry.register(new StringSerializer());
        return registry;
    }

    @Test
    void validateUsesDefaultAsEffectiveValue() {
        var result = binder.validate(section(Map.of()), DefaultedPortConfig.class);

        assertTrue(result.isValid(), result::format);
        assertEquals(
                3306, binder.bind(section(Map.of()), DefaultedPortConfig.class).port());
    }

    @Test
    void validateConvertsStringValueBeforeRangeCheck() {
        var result = binder.validate(section(Map.of("port", "3306")), DefaultedPortConfig.class);

        assertTrue(result.isValid(), result::format);
    }

    @Test
    void validateReportsDefaultOutsideRange() {
        var result = binder.validate(section(Map.of()), OutOfRangeDefaultConfig.class);

        assertTrue(result.hasErrors());
        assertTrue(result.issues().getFirst().message().contains("greater than or equal to 1024"));
    }

    @Test
    void validateReportsImpossibleRangeAnnotation() {
        var result = binder.validate(section(Map.of("count", 5)), ImpossibleRangeConfig.class);

        assertTrue(result.hasErrors());
        assertTrue(result.issues().getFirst().message().contains("invalid range annotation"));
    }

    @Test
    void validateRejectsRangeOnNonNumericComponent() {
        var result = binder.validate(section(Map.of("name", "cotani")), TextRangeConfig.class);

        assertTrue(result.hasErrors());
        assertTrue(result.issues().getFirst().message().contains("@Range can only be used with numeric components"));
    }

    @Test
    void validateSupportsPrivateNestedRecords() {
        var section = section(Map.of("message", "hello"));
        var result = binder.validate(section, PrivateConfigRecord.class);
        assertTrue(result.isValid(), result::format);

        var bound = binder.bind(section, PrivateConfigRecord.class);
        assertEquals("hello", bound.message());
    }

    private record PrivateConfigRecord(@Required String message) {}

    private ConfigSection section(Map<String, Object> values) {
        return new ConfigSection("test.yml", "", new MapConfigSource(values), serializers, binder);
    }

    private static final class MapConfigSource implements ConfigSource {

        private final Map<String, Object> values;

        private MapConfigSource(Map<String, Object> values) {
            this.values = Map.copyOf(values);
        }

        @Override
        public Path path() {
            return Path.of("test.yml");
        }

        @Override
        public void load() {
            // Test config source does not load from disk.
        }

        @Override
        public void save() {
            // Test config source does not persist to disk.
        }

        @Override
        public boolean contains(String path) {
            return entry(path).exists();
        }

        @Override
        public @Nullable Object get(String path) {
            return entry(path).raw();
        }

        @Override
        public Entry entry(String path) {
            if (path == null || path.isBlank()) {
                return new Entry(values, true);
            }
            Object current = values;
            for (var part : parts(path)) {
                if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) {
                    return new Entry(null, false);
                }
                current = map.get(part);
            }
            return new Entry(current, true);
        }

        private List<String> parts(String path) {
            List<String> result = new ArrayList<>();
            var start = 0;
            for (var index = 0; index < path.length(); index++) {
                if (path.charAt(index) == '.') {
                    result.add(path.substring(start, index));
                    start = index + 1;
                }
            }
            result.add(path.substring(start));
            return result;
        }

        @Override
        public void set(String path, @Nullable Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean setIfMissing(String path, @Nullable Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<String> keys(String path) {
            return values.keySet();
        }

        @Override
        public Map<String, Object> section(String path) {
            Object value = entry(path).raw();
            if (!(value instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> result.put(String.valueOf(key), nestedValue));
            return result;
        }

        @Override
        public List<Object> list(String path) {
            Object value = entry(path).raw();
            if (value instanceof List<?> list) {
                return List.copyOf(list);
            }
            return List.of();
        }
    }
}

record DefaultedPortConfig(
        @Required @Default("3306") @Range(min = 1024, max = 65535)
        int port) {}

record OutOfRangeDefaultConfig(
        @Default("1") @Range(min = 1024, max = 65535) int port) {}

record ImpossibleRangeConfig(@Range(min = 10, max = 1) int count) {}

record TextRangeConfig(@Range(min = 1, max = 5) String name) {}
