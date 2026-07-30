package com.cotani.config.value;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.serializer.defaults.BooleanSerializer;
import com.cotani.config.serializer.defaults.DurationSerializer;
import com.cotani.config.serializer.defaults.IntegerSerializer;
import com.cotani.config.serializer.defaults.StringSerializer;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigValueTest {

    private ConfigSerializerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ConfigSerializerRegistry();
        registry.register(new StringSerializer());
        registry.register(new IntegerSerializer());
        registry.register(new BooleanSerializer());
        registry.register(new DurationSerializer());
    }

    @Test
    void asStringReturnsValueWhenExists() {
        var value = ConfigValue.create("test.yml", "path", "hello", true, registry);
        assertEquals("hello", value.asString());
    }

    @Test
    void asStringThrowsWhenNotExists() {
        var value = ConfigValue.create("test.yml", "path", null, false, registry);
        assertThrows(ConfigException.class, value::asString);
    }

    @Test
    void asStringThrowsWhenRawNull() {
        var value = ConfigValue.create("test.yml", "path", null, true, registry);
        assertThrows(ConfigException.class, value::asString);
    }

    @Test
    void asIntReturnsValue() {
        var value = ConfigValue.create("test.yml", "count", 42, true, registry);
        assertEquals(42, value.asInt());
    }

    @Test
    void asIntConvertsString() {
        var value = ConfigValue.create("test.yml", "count", "42", true, registry);
        assertEquals(42, value.asInt());
    }

    @Test
    void asBooleanReturnsValue() {
        var value = ConfigValue.create("test.yml", "flag", true, true, registry);
        assertTrue(value.asBoolean());
    }

    @Test
    void asDurationParsesString() {
        var value = ConfigValue.create("test.yml", "timeout", "500ms", true, registry);
        assertEquals(Duration.ofMillis(500), value.asDuration());
    }

    @Test
    void asDurationReturnsDurationDirectly() {
        var value = ConfigValue.create("test.yml", "timeout", Duration.ofSeconds(3), true, registry);
        assertEquals(Duration.ofSeconds(3), value.asDuration());
    }

    @Test
    void locationReturnsFileAndPath() {
        var value = ConfigValue.create("config.yml", "some.nested.path", "val", true, registry);
        assertEquals("config.yml:some.nested.path", value.location());
    }

    @Test
    @SuppressWarnings("NullAway")
    void asThrowsForMissingValue() {
        var value = ConfigValue.create("test.yml", "path", null, false, registry);
        var ex = assertThrows(ConfigException.class, () -> value.as(String.class));
        assertTrue(ex.getMessage().contains("Missing"));
    }

    @Test
    @SuppressWarnings("NullAway")
    void constructorAcceptsNullRaw() {
        var value = ConfigValue.create("test.yml", "path", null, false, registry);
        assertNull(value.raw());
        assertFalse(value.exists());
    }
}
