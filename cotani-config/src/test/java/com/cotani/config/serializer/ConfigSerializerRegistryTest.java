package com.cotani.config.serializer;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.defaults.IntegerSerializer;
import com.cotani.config.serializer.defaults.StringSerializer;
import com.cotani.config.value.ConfigValue;
import org.junit.jupiter.api.Test;

class ConfigSerializerRegistryTest {

    private final ConfigSerializerRegistry registry = new ConfigSerializerRegistry();

    @Test
    void convertReturnsRegisteredSerializerResult() {
        registry.register(new IntegerSerializer());
        var value = new ConfigValue("test.yml", "n", "42", true, registry);
        assertEquals(42, registry.convert(value, Integer.class));
    }

    @Test
    void convertThrowsForUnsupportedType() {
        var value = new ConfigValue("test.yml", "x", "hello", true, registry);
        assertThrows(ConfigException.class, () -> registry.convert(value, getClass()));
    }

    @Test
    void convertFallsBackToRawCast() {
        var value = new ConfigValue("test.yml", "x", "hello", true, registry);
        assertEquals("hello", registry.convert(value, String.class));
    }

    @Test
    @SuppressWarnings("NullAway")
    void serializeReturnsNullForNullInput() {
        assertNull(registry.serialize(null));
    }

    @Test
    void serializeReturnsRawForUnknownType() {
        assertEquals(42, registry.serialize(42));
    }

    @Test
    void serializeUsesRegisteredSerializer() {
        registry.register(new StringSerializer());
        assertEquals("hello", registry.serialize("hello"));
    }

    @Test
    void registerIsThreadSafe() {
        var registry = new ConfigSerializerRegistry();
        var threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    registry.register(new IntegerSerializer());
                }
            });
            threads[i].start();
        }
        for (var t : threads) {
            try {
                t.join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        var value = new ConfigValue("test.yml", "n", "42", true, registry);
        assertEquals(42, registry.convert(value, Integer.class));
    }
}
