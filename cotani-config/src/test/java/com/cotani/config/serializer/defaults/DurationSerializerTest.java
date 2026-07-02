package com.cotani.config.serializer.defaults;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.config.exception.ConfigException;
import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.value.ConfigValue;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DurationSerializerTest {

    private ConfigSerializerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ConfigSerializerRegistry();
        registry.register(new DurationSerializer());
    }

    @Test
    void readsMillis() {
        var value = valueOf("500ms");
        assertEquals(Duration.ofMillis(500), registry.convert(value, Duration.class));
    }

    @Test
    void readsSeconds() {
        var value = valueOf("3s");
        assertEquals(Duration.ofSeconds(3), registry.convert(value, Duration.class));
    }

    @Test
    void readsMinutes() {
        var value = valueOf("5m");
        assertEquals(Duration.ofMinutes(5), registry.convert(value, Duration.class));
    }

    @Test
    void readsHours() {
        var value = valueOf("2h");
        assertEquals(Duration.ofHours(2), registry.convert(value, Duration.class));
    }

    @Test
    void readsDays() {
        var value = valueOf("1d");
        assertEquals(Duration.ofDays(1), registry.convert(value, Duration.class));
    }

    @Test
    void readsIso8601() {
        var value = valueOf("PT1H30M");
        assertEquals(Duration.ofHours(1).plusMinutes(30), registry.convert(value, Duration.class));
    }

    @Test
    void rejectsInvalidFormat() {
        var value = valueOf("invalid");
        assertThrows(ConfigException.class, () -> registry.convert(value, Duration.class));
    }

    @Test
    void writesDurationBack() {
        var serializer = new DurationSerializer();
        assertEquals("500ms", serializer.write(Duration.ofMillis(500)));
        assertEquals("3s", serializer.write(Duration.ofSeconds(3)));
        assertEquals("5m", serializer.write(Duration.ofMinutes(5)));
        assertEquals("2h", serializer.write(Duration.ofHours(2)));
        assertEquals("1d", serializer.write(Duration.ofDays(1)));
        assertEquals("0ms", serializer.write(Duration.ZERO));
    }

    private ConfigValue valueOf(String raw) {
        return new ConfigValue("test.yml", "duration", raw, true, registry);
    }
}
