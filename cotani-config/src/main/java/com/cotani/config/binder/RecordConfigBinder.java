package com.cotani.config.binder;

import com.cotani.config.annotation.ConfigPath;
import com.cotani.config.annotation.ConfigType;
import com.cotani.config.annotation.Default;
import com.cotani.config.exception.ConfigException;
import com.cotani.config.section.ConfigSection;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.source.ConfigSource;
import com.cotani.config.validation.ConfigValidator;
import com.cotani.config.validation.ValidationResult;
import com.cotani.config.value.ConfigValue;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

public final class RecordConfigBinder implements ConfigBinder {

    private final ConfigSerializerRegistry serializers;
    private final ConfigValidator validator;
    private final Map<Class<?>, RecordBindingPlan<?>> plans = new ConcurrentHashMap<>();

    public RecordConfigBinder(ConfigSerializerRegistry serializers) {
        this.serializers = Objects.requireNonNull(serializers, "serializers");
        this.validator = new ConfigValidator(serializers);
    }

    @Override
    public <T> T bind(ConfigSection section, Class<T> type) {
        if (type.isRecord()) {
            return bindRecord(section, type);
        }
        if (type.isSealed()) {
            return bindSealed(section, type);
        }
        throw new ConfigException("Only record and sealed config types are supported: " + type.getName());
    }

    @Override
    public <T> ValidationResult validate(ConfigSection section, Class<T> type) {
        var result = ValidationResult.valid();
        if (type.isSealed()) {
            Class<?> selected = selectSealedImplementation(section, type);
            return validate(section, selected);
        }
        if (!type.isRecord()) {
            return result;
        }
        for (var component : type.getRecordComponents()) {
            var path = pathFor(component);
            var value = section.value(path);
            var readableValue = valueForDefault(component, value);
            result.merge(validator.validateComponent(readableValue, component));
        }
        return result;
    }

    private <T> T bindRecord(ConfigSection section, Class<T> type) {
        RecordBindingPlan<T> plan = plan(type);
        Object[] values = plan.components().stream()
                .map(component -> readComponent(section, component))
                .toArray();
        try {
            return plan.constructor().newInstance(values);
        } catch (ReflectiveOperationException exception) {
            throw new ConfigException("Could not bind config record " + type.getName(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> RecordBindingPlan<T> plan(Class<T> type) {
        return (RecordBindingPlan<T>) plans.computeIfAbsent(type, this::createPlan);
    }

    private <T> RecordBindingPlan<T> createPlan(Class<T> type) {
        try {
            var components = type.getRecordComponents();
            var parameterTypes = new Class<?>[components.length];
            var componentList = new ArrayList<RecordComponent>(components.length);
            for (var index = 0; index < components.length; index++) {
                parameterTypes[index] = components[index].getType();
                componentList.add(components[index]);
            }
            var constructor = type.getDeclaredConstructor(parameterTypes);
            return new RecordBindingPlan<>(type, constructor, List.copyOf(componentList));
        } catch (ReflectiveOperationException exception) {
            throw new ConfigException("Could not create binding plan for " + type.getName(), exception);
        }
    }

    private Object readComponent(ConfigSection section, RecordComponent component) {
        var path = pathFor(component);
        var value = section.value(path);
        var readableValue = valueForDefault(component, value);
        Class<?> componentType = component.getType();
        Type genericType = component.getGenericType();
        if (componentType.isRecord()) {
            if (!readableValue.exists()) {
                throw new ConfigException("Missing section '" + path + "' required by record " + componentType.getName()
                        + " at " + section.path());
            }
            return bindRecord(section.section(path), componentType);
        }
        if (componentType.isSealed()) {
            if (!readableValue.exists()) {
                throw new ConfigException("Missing section '" + path + "' required by sealed type "
                        + componentType.getName() + " at " + section.path());
            }
            return bindSealed(section.section(path), componentType);
        }
        if (List.class.isAssignableFrom(componentType)) {
            return readList(genericType, readableValue);
        }
        if (Map.class.isAssignableFrom(componentType)) {
            return readMap(genericType, readableValue);
        }
        return serializers.convert(readableValue, componentType);
    }

    private ConfigValue valueForDefault(RecordComponent component, ConfigValue value) {
        if (value.exists() && value.raw() != null) {
            return value;
        }
        Default defaultValue = component.getAnnotation(Default.class);
        if (defaultValue == null) {
            return value;
        }
        return new ConfigValue(value.file(), value.path(), defaultValue.value(), true, serializers);
    }

    private List<?> readList(Type genericType, ConfigValue value) {
        var itemType = genericArgument(genericType, 0);
        var raw = value.exists() ? value.raw() : null;
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            throw new ConfigException("Expected a list at " + value.location());
        }
        return IntStream.range(0, list.size())
                .mapToObj(index -> {
                    var element = list.get(index);
                    ConfigValue elementValue = new ConfigValue(
                            value.file(), value.path() + "[" + index + "]", element, element != null, serializers);
                    return convertElement(elementValue, itemType);
                })
                .toList();
    }

    private Map<String, ?> readMap(Type genericType, ConfigValue value) {
        var valueType = genericArgument(genericType, 1);
        var raw = value.exists() ? value.raw() : null;
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new ConfigException("Expected a map at " + value.location());
        }
        return map.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(entry -> String.valueOf(entry.getKey()), entry -> {
                    var entryValue = entry.getValue();
                    ConfigValue configValue = new ConfigValue(
                            value.file(),
                            value.path() + "." + entry.getKey(),
                            entryValue,
                            entryValue != null,
                            serializers);
                    return convertElement(configValue, valueType);
                }));
    }

    private Object convertElement(ConfigValue elementValue, Class<?> itemType) {
        if (itemType.isRecord() || itemType.isSealed()) {
            var subSection = sectionFromElement(elementValue);
            if (itemType.isRecord()) {
                return bindRecord(subSection, itemType);
            }
            return bindSealed(subSection, itemType);
        }
        return serializers.convert(elementValue, itemType);
    }

    private ConfigSection sectionFromElement(ConfigValue elementValue) {
        Object raw = elementValue.raw();
        if (!(raw instanceof Map<?, ?> map)) {
            throw new ConfigException("Expected a section at " + elementValue.location());
        }

        Map<String, Object> values = new LinkedHashMap<>();
        map.forEach((key, value) -> values.put(String.valueOf(key), value));
        return new ConfigSection(
                elementValue.file(),
                elementValue.path(),
                new InMemoryConfigSource(elementValue.file(), elementValue.path(), values),
                serializers,
                this);
    }

    @SuppressWarnings("unchecked")
    private <T> T bindSealed(ConfigSection section, Class<T> type) {
        var implementation = selectSealedImplementation(section, type);
        if (!implementation.isRecord()) {
            throw new ConfigException("Sealed implementation " + implementation.getName() + " must be a record");
        }
        return (T) bindRecord(section, implementation);
    }

    private Class<?> selectSealedImplementation(ConfigSection section, Class<?> type) {
        var typeValue = section.value("type");
        if (!typeValue.exists()) {
            throw new ConfigException("Missing 'type' field in " + type.getName() + " at " + section.path());
        }
        var requested = typeValue.asString().trim().toUpperCase(Locale.ROOT);
        var permittedSubclasses = type.getPermittedSubclasses();
        if (permittedSubclasses == null) {
            throw new ConfigException(type.getName() + " is not a sealed class");
        }
        for (var permitted : permittedSubclasses) {
            ConfigType configType = permitted.getAnnotation(ConfigType.class);
            if (configType == null) {
                continue;
            }
            var name = configType.value().trim().toUpperCase(Locale.ROOT);
            if (name.equals(requested)) {
                return permitted;
            }
        }
        throw new ConfigException("Unknown config type " + requested + " for " + type.getName());
    }

    private static String pathFor(RecordComponent component) {
        ConfigPath configPath = component.getAnnotation(ConfigPath.class);
        if (configPath != null) {
            return configPath.value();
        }
        return toKebabCase(component.getName());
    }

    private static String toKebabCase(String input) {
        return input.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
                .toLowerCase(Locale.ROOT);
    }

    private static Class<?> genericArgument(Type type, int index) {
        if (!(type instanceof ParameterizedType parameterizedType)) {
            return Object.class;
        }
        var args = parameterizedType.getActualTypeArguments();
        if (index < 0 || index >= args.length) {
            return Object.class;
        }
        var argument = args[index];
        if (argument instanceof Class<?> clazz) {
            return clazz;
        }
        if (argument instanceof ParameterizedType nested) {
            return (Class<?>) nested.getRawType();
        }
        return Object.class;
    }

    private static final class InMemoryConfigSource implements ConfigSource {

        private final Path path;
        private final String rootPath;
        private final Map<String, Object> values;

        private InMemoryConfigSource(String file, String rootPath, Map<String, Object> values) {
            this.path = Path.of(file);
            this.rootPath = rootPath;
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
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
        public List<?> list(String path) {
            Object value = resolve(path).raw();
            if (value instanceof List<?> list) {
                return List.copyOf(list);
            }
            return List.of();
        }

        private Entry resolve(String requestedPath) {
            var relativePath = relativePath(requestedPath);
            if (relativePath.isBlank()) {
                return new Entry(values, true);
            }

            Object current = values;
            for (var part : parts(relativePath)) {
                if (!(current instanceof Map<?, ?> map)) {
                    return new Entry(null, false);
                }
                if (!map.containsKey(part)) {
                    return new Entry(null, false);
                }
                current = map.get(part);
            }
            return new Entry(current, true);
        }

        private String relativePath(String requestedPath) {
            if (requestedPath == null || requestedPath.isBlank()) {
                return "";
            }
            if (requestedPath.equals(rootPath)) {
                return "";
            }
            var prefix = rootPath + ".";
            if (requestedPath.startsWith(prefix)) {
                return requestedPath.substring(prefix.length());
            }
            return requestedPath;
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
}
