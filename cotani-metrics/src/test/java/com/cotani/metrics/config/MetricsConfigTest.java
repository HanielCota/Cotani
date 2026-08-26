package com.cotani.metrics.config;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.config.serializer.ConfigSerializerRegistry;
import com.cotani.config.serializer.defaults.IntegerSerializer;
import com.cotani.config.validation.ConfigValidator;
import com.cotani.config.validation.ValidationResult;
import com.cotani.config.value.ConfigValue;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Verifies defaults, normalization and the declared {@code @Range} validation of
 * {@link MetricsConfig}.
 */
class MetricsConfigTest {

    private static final ConfigSerializerRegistry SERIALIZERS = serializersWithInteger();

    private static ConfigSerializerRegistry serializersWithInteger() {
        var registry = new ConfigSerializerRegistry();
        registry.register(new IntegerSerializer());
        return registry;
    }

    private static ValidationResult validatePort(int port) {
        ConfigValidator validator = ConfigValidator.create(SERIALIZERS);
        RecordComponent portComponent = Arrays.stream(MetricsConfig.class.getRecordComponents())
                .filter(component -> component.getName().equals("port"))
                .findFirst()
                .orElseThrow();

        ConfigValue value = ConfigValue.create("config.yml", "metrics.port", port, true, SERIALIZERS);

        return validator.validateComponent(value, portComponent);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldApplyDefaultsForNullComponents() {
        MetricsConfig config = new MetricsConfig(false, null, null, 9090, null);

        assertFalse(config.enabled());
        assertEquals("cotani", config.prefix());
        assertEquals(MetricsConfig.DEFAULT_HOST, config.host());
        assertEquals(9090, config.port());
        assertEquals(MetricsConfig.DEFAULT_PATH, config.path());
    }

    @Test
    void shouldApplyDefaultsForBlankComponents() {
        MetricsConfig config = new MetricsConfig(false, "   ", "   ", 9090, "   ");

        assertFalse(config.enabled());
        assertEquals("cotani", config.prefix());
        assertEquals(MetricsConfig.DEFAULT_HOST, config.host());
        assertEquals(9090, config.port());
        assertEquals(MetricsConfig.DEFAULT_PATH, config.path());
    }

    @Test
    void shouldPrependSlashToPathWithoutLeadingSlash() {
        MetricsConfig config = new MetricsConfig(false, "cotani", 9090, "metrics");

        assertEquals("/metrics", config.path());
    }

    @Test
    void shouldPreservePathWithLeadingSlash() {
        MetricsConfig config = new MetricsConfig(false, "cotani", 9090, "/custom");

        assertEquals("/custom", config.path());
    }

    @Test
    void shouldExposeConfiguredValues() {
        MetricsConfig config = new MetricsConfig(true, "app", 8080, "/scrape");

        assertTrue(config.enabled());
        assertEquals("app", config.prefix());
        assertEquals(8080, config.port());
        assertEquals("/scrape", config.path());
    }

    @Test
    void shouldUseDefaultHostInConvenienceConstructor() {
        MetricsConfig config = new MetricsConfig(false, "cotani", 9090, "/metrics");

        assertEquals(MetricsConfig.DEFAULT_HOST, config.host());
    }

    @Test
    void shouldAcceptPortWithinDeclaredRangeAtBindTime() {
        assertTrue(validatePort(1024).isValid());
        assertTrue(validatePort(9090).isValid());
        assertTrue(validatePort(65535).isValid());
    }

    @Test
    void shouldRejectPortBelowDeclaredRangeAtBindTime() {
        ValidationResult result = validatePort(1023);

        assertTrue(result.hasErrors());
        assertTrue(result.issues().get(0).message().contains("1024"));
    }

    @Test
    void shouldRejectPortAboveDeclaredRangeAtBindTime() {
        ValidationResult result = validatePort(65536);

        assertTrue(result.hasErrors());
        assertTrue(result.issues().get(0).message().contains("65535"));
    }
}
