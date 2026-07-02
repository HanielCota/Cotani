package com.cotani.config.value;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigValueTest {

    private ConfigSerializerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ConfigSerializerRegistry();
        registry.register(new com.cotani.config.serializer.defaults.StringSerializer());
        registry.register(new com.cotani.config.serializer.defaults.IntegerSerializer());
        registry.register(new com.cotani.config.serializer.defaults.BooleanSerializer());
        registry.register(new com.cotani.config.serializer.defaults.DurationSerializer());
    }

    @Test
    void asStringReturnsValueWhenExists() {
        var value = new ConfigValue("test.yml", "path", "hello", true, registry);
        assertEquals("hello", value.asString());
    }

    @Test
    void asStringThrowsWhenNotExists() {
        var value = new ConfigValue("test.yml", "path", null, false, registry);
        assertThrows(ConfigException.class, value::asString);
    }

    @Test
    void asStringThrowsWhenRawNull() {
        var value = new ConfigValue("test.yml", "path", null, true, registry);
        assertThrows(ConfigException.class, value::asString);
    }

    @Test
    void asIntReturnsValue() {
        var value = new ConfigValue("test.yml", "count", 42, true, registry);
        assertEquals(42, value.asInt());
    }

    @Test
    void asIntConvertsString() {
        var value = new ConfigValue("test.yml", "count", "42", true, registry);
        assertEquals(42, value.asInt());
    }

    @Test
    void asBooleanReturnsValue() {
        var value = new ConfigValue("test.yml", "flag", true, true, registry);
        assertTrue(value.asBoolean());
    }

    @Test
    void asDurationParsesString() {
        var value = new ConfigValue("test.yml", "timeout", "500ms", true, registry);
        assertEquals(Duration.ofMillis(500), value.asDuration());
    }

    @Test
    void asDurationReturnsDurationDirectly() {
        var value = new ConfigValue("test.yml", "timeout", Duration.ofSeconds(3), true, registry);
        assertEquals(Duration.ofSeconds(3), value.asDuration());
    }

    @Test
    void locationReturnsFileAndPath() {
        var value = new ConfigValue("config.yml", "some.nested.path", "val", true, registry);
        assertEquals("config.yml:some.nested.path", value.location());
    }

    @Test
    @SuppressWarnings("NullAway")
    void asThrowsForMissingValue() {
        var value = new ConfigValue("test.yml", "path", null, false, registry);
        var ex = assertThrows(ConfigException.class, () -> value.as(String.class));
        assertTrue(ex.getMessage().contains("Missing"));
    }

    @Test
    @SuppressWarnings("NullAway")
    void constructorAcceptsNullRaw() {
        var value = new ConfigValue("test.yml", "path", null, false, registry);
        assertNull(value.raw());
        assertFalse(value.exists());
    }
}
