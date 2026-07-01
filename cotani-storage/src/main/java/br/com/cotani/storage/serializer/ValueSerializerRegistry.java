package br.com.cotani.storage.serializer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public final class ValueSerializerRegistry {

    private final Map<Class<?>, ValueSerializer<?>> serializers = new ConcurrentHashMap<>();

    public ValueSerializerRegistry() {
        register(new UuidSerializer());
        register(new InstantSerializer());
        register(new DurationSerializer());
        register(new ComponentSerializer());
    }

    public <T> void register(ValueSerializer<T> serializer) {
        serializers.put(serializer.type(), serializer);
    }

    public Object serialize(Object value) {
        if (value == null) {
            return null;
        }

        ValueSerializer<Object> serializer = findSerializer(value.getClass());
        if (serializer == null) {
            return value;
        }

        return serializer.serialize(value);
    }

    public <T> T deserialize(Object value, Class<T> type) {
        if (value == null) {
            return null;
        }

        ValueSerializer<T> serializer = findSerializer(type);
        if (serializer == null) {
            return type.cast(value);
        }

        return serializer.deserialize(value);
    }

    @SuppressWarnings("unchecked")
    private <T> ValueSerializer<T> findSerializer(Class<?> type) {
        ValueSerializer<?> exact = serializers.get(type);
        if (exact != null) {
            return (ValueSerializer<T>) exact;
        }

        for (ValueSerializer<?> serializer : serializers.values()) {
            if (serializer.type().isAssignableFrom(type)) {
                return (ValueSerializer<T>) serializer;
            }
        }

        return null;
    }

    private static final class UuidSerializer implements ValueSerializer<UUID> {
        @Override
        public Class<UUID> type() {
            return UUID.class;
        }

        @Override
        public Object serialize(UUID value) {
            return value.toString();
        }

        @Override
        public UUID deserialize(Object value) {
            return UUID.fromString(String.valueOf(value));
        }
    }

    private static final class InstantSerializer implements ValueSerializer<Instant> {
        @Override
        public Class<Instant> type() {
            return Instant.class;
        }

        @Override
        public Object serialize(Instant value) {
            return value.toString();
        }

        @Override
        public Instant deserialize(Object value) {
            return Instant.parse(String.valueOf(value));
        }
    }

    private static final class DurationSerializer implements ValueSerializer<Duration> {
        @Override
        public Class<Duration> type() {
            return Duration.class;
        }

        @Override
        public Object serialize(Duration value) {
            return value.toMillis();
        }

        @Override
        public Duration deserialize(Object value) {
            return Duration.ofMillis(Long.parseLong(String.valueOf(value)));
        }
    }

    private static final class ComponentSerializer implements ValueSerializer<Component> {
        @Override
        public Class<Component> type() {
            return Component.class;
        }

        @Override
        public Object serialize(Component value) {
            return GsonComponentSerializer.gson().serialize(value);
        }

        @Override
        public Component deserialize(Object value) {
            return GsonComponentSerializer.gson().deserialize(String.valueOf(value));
        }
    }
}
