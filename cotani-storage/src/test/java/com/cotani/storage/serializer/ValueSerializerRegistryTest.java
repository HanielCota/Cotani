package com.cotani.storage.serializer;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class ValueSerializerRegistryTest {

    @Test
    void unregisteredTypeIsReturnedUnchangedOnRepeatedLookup() {
        ValueSerializerRegistry registry = new ValueSerializerRegistry();
        var value = new Object();

        assertEquals(value, registry.serialize(value));
        assertEquals(value, registry.serialize(value));
    }

    @Test
    void uuidIsSerializedToString() {
        ValueSerializerRegistry registry = new ValueSerializerRegistry();
        UUID value = UUID.randomUUID();

        Object serialized = registry.serialize(value);

        assertEquals(value.toString(), serialized);
        assertEquals(value, registry.deserialize(serialized, UUID.class));
    }

    @Test
    void instantIsSerializedToString() {
        ValueSerializerRegistry registry = new ValueSerializerRegistry();
        Instant value = Instant.now();

        Object serialized = registry.serialize(value);

        assertEquals(value.toString(), serialized);
        assertEquals(value, registry.deserialize(serialized, Instant.class));
    }

    @Test
    void durationIsSerializedToMillis() {
        ValueSerializerRegistry registry = new ValueSerializerRegistry();
        Duration value = Duration.ofSeconds(123);

        Object serialized = registry.serialize(value);

        assertEquals(123_000L, serialized);
        assertEquals(value, registry.deserialize(serialized, Duration.class));
    }

    @Test
    void componentIsSerializedToJson() {
        ValueSerializerRegistry registry = new ValueSerializerRegistry();
        Component value = Component.text("hello");

        Object serialized = registry.serialize(value);

        assertTrue(serialized instanceof String);
        assertEquals(value, registry.deserialize(serialized, Component.class));
    }

    @Test
    void dynamicRegistrationIsReflectedOnSubsequentLookups() {
        ValueSerializerRegistry registry = new ValueSerializerRegistry();
        var value = new Wrapper("wrapped");

        assertSame(value, registry.serialize(value));

        registry.register(new WrapperSerializer());

        assertEquals("wrapped", registry.serialize(value));
        assertEquals(value, registry.deserialize("wrapped", Wrapper.class));
    }

    private record Wrapper(String value) {}

    private static final class WrapperSerializer implements ValueSerializer<Wrapper> {
        @Override
        public Class<Wrapper> type() {
            return Wrapper.class;
        }

        @Override
        public Object serialize(Wrapper value) {
            return value.value();
        }

        @Override
        public Wrapper deserialize(Object value) {
            return new Wrapper(String.valueOf(value));
        }
    }
}
