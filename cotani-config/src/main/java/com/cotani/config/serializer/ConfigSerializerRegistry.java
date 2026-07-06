package com.cotani.config.serializer;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.defaults.*;
import com.cotani.config.value.ConfigValue;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

public final class ConfigSerializerRegistry {

    private final AtomicReference<Map<Class<?>, ConfigSerializer<?>>> serializers =
            new AtomicReference<>(new LinkedHashMap<>());
    private final ClassValue<Optional<ConfigSerializer<?>>> resolvedCache = new ClassValue<>() {
        @Override
        protected Optional<ConfigSerializer<?>> computeValue(Class<?> type) {
            return Optional.ofNullable(resolve(type));
        }
    };

    public static ConfigSerializerRegistry defaults(Plugin plugin) {
        var registry = new ConfigSerializerRegistry();
        registry.register(new StringSerializer());
        registry.register(new IntegerSerializer());
        registry.register(new LongSerializer());
        registry.register(new DoubleSerializer());
        registry.register(new FloatSerializer());
        registry.register(new BooleanSerializer());
        registry.register(new DurationSerializer());
        registry.register(new PathSerializer(plugin.getDataFolder().toPath()));
        registry.register(new UuidSerializer());
        registry.register(new ComponentSerializer());
        registry.register(new MaterialSerializer());
        registry.register(new SoundSerializer());
        registry.register(new NamespacedKeySerializer(plugin));
        registry.register(new KeySerializer());
        return registry;
    }

    public <T> void register(ConfigSerializer<T> serializer) {
        synchronized (this) {
            var next = new LinkedHashMap<>(Objects.requireNonNull(serializers.get()));
            next.put(serializer.type(), serializer);
            serializers.set(Collections.unmodifiableMap(next));
        }
    }

    public Optional<ConfigSerializer<?>> find(Class<?> type) {
        return resolvedCache.get(wrap(type));
    }

    private @Nullable ConfigSerializer<?> resolve(Class<?> wrapped) {
        var currentSerializers = Objects.requireNonNull(serializers.get());
        ConfigSerializer<?> serializer = currentSerializers.get(wrapped);
        if (serializer != null) {
            return serializer;
        }
        for (var entry : currentSerializers.entrySet()) {
            if (entry.getKey().isAssignableFrom(wrapped)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked"})
    public <T> T convert(ConfigValue value, Class<T> type) {
        Class<T> wrapped = wrap(type);
        Optional<ConfigSerializer<?>> serializer = find(wrapped);
        if (serializer.isPresent()) {
            return (T) serializer.get().read(value);
        }
        if (wrapped.isEnum()) {
            @SuppressWarnings("unchecked")
            var enumType = (Class<? extends Enum<?>>) wrapped;
            return (T) readEnum(value, enumType);
        }
        if (value.raw() != null && wrapped.isInstance(value.raw())) {
            return wrapped.cast(value.raw());
        }
        throw new ConfigException("Unsupported config type " + wrapped.getName() + " at " + value.location());
    }

    @Nullable
    public Object serialize(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        Optional<ConfigSerializer<?>> serializer = find(value.getClass());
        return serializer
                .map(configSerializer -> write(configSerializer, value))
                .orElse(value);
    }

    @SuppressWarnings("unchecked")
    private <T> Object write(ConfigSerializer<T> serializer, Object value) {
        return serializer.write((T) value);
    }

    @SuppressWarnings("unchecked")
    private <T> Class<T> wrap(Class<T> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return (Class<T>)
                switch (type.getName()) {
                    case "int" -> Integer.class;
                    case "long" -> Long.class;
                    case "double" -> Double.class;
                    case "float" -> Float.class;
                    case "boolean" -> Boolean.class;
                    case "byte" -> Byte.class;
                    case "short" -> Short.class;
                    case "char" -> Character.class;
                    default -> throw new ConfigException("Unsupported primitive type " + type.getName());
                };
    }

    private Enum<?> readEnum(ConfigValue value, Class<? extends Enum<?>> type) {
        String name = value.asString().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        var constants = type.getEnumConstants();
        if (constants == null) {
            throw new ConfigException("Not an enum type " + type.getName() + " at " + value.location());
        }
        return Arrays.stream(constants)
                .filter(constant -> constant.name().equals(name))
                .findFirst()
                .orElseThrow(
                        () -> new ConfigException("Invalid enum value " + value.raw() + " at " + value.location()));
    }
}
