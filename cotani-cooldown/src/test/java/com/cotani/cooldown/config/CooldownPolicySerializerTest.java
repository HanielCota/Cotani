package com.cotani.cooldown.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.serializer.defaults.DurationSerializer;
import com.cotani.config.value.ConfigValue;
import com.cotani.cooldown.api.CooldownPolicy;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CooldownPolicySerializerTest {
    private ConfigSerializerRegistry registry;
    private CooldownPolicySerializer serializer;

    @BeforeEach
    void setUp() {
        registry = new ConfigSerializerRegistry();
        var durationSerializer = new DurationSerializer();
        registry.register(durationSerializer);
        serializer = new CooldownPolicySerializer(durationSerializer);
    }

    @Test
    void shouldReadPolicyDuration() {
        var policy = serializer.read(value("5m"));

        assertEquals(Duration.ofMinutes(5), policy.duration());
    }

    @Test
    void shouldWritePolicyDuration() {
        CooldownPolicy policy = () -> Duration.ofHours(2);

        assertEquals("2h", serializer.write(policy));
    }

    @Test
    void shouldRoundTripThroughWriteAndRead() {
        CooldownPolicy policy = () -> Duration.ofSeconds(45);

        var written = serializer.write(policy);
        var reread = serializer.read(value((String) written));

        assertEquals(policy.duration(), reread.duration());
    }

    @Test
    void shouldExposeSerializedType() {
        assertEquals(CooldownPolicy.class, serializer.type());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullValue() {
        assertThrows(NullPointerException.class, () -> serializer.read(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullPolicy() {
        assertThrows(NullPointerException.class, () -> serializer.write(null));
    }

    private ConfigValue value(String raw) {
        return ConfigValue.create("config.yml", "cooldowns.command", raw, true, registry);
    }
}
