package com.cotani.cache.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class CacheSettingsBuilderTest {
    @Test
    void defaultsMatchDocumentedValues() {
        CacheSettings settings = CacheSettings.builder().build();

        assertEquals(10_000, settings.maximumSize());
        assertEquals(Duration.ZERO, settings.expireAfterAccess());
        assertEquals(Duration.ZERO, settings.expireAfterWrite());
        assertEquals(Duration.ZERO, settings.autosaveInterval());
        assertFalse(settings.loadOnJoin());
        assertFalse(settings.saveOnQuit());
        assertFalse(settings.unloadOnQuit());
        assertFalse(settings.saveOnEvict());
        assertFalse(settings.recordStats());
        assertFalse(settings.autosaveEnabled());
        assertFalse(settings.expireAfterAccessEnabled());
        assertFalse(settings.expireAfterWriteEnabled());
    }

    @Test
    void everySetterReturnsSameBuilderForChaining() {
        CacheSettingsBuilder builder = CacheSettings.builder();

        assertSame(builder, builder.maximumSize(100));
        assertSame(builder, builder.expireAfterAccess(Duration.ofMinutes(1)));
        assertSame(builder, builder.expireAfterWrite(Duration.ofMinutes(2)));
        assertSame(builder, builder.autosaveInterval(Duration.ofMinutes(3)));
        assertSame(builder, builder.loadOnJoin(true));
        assertSame(builder, builder.saveOnQuit(true));
        assertSame(builder, builder.unloadOnQuit(true));
        assertSame(builder, builder.saveOnEvict(true));
        assertSame(builder, builder.recordStats(true));
    }

    @Test
    void buildReflectsConfiguredValues() {
        CacheSettings settings = CacheSettings.builder()
                .maximumSize(250)
                .expireAfterAccess(Duration.ofMinutes(5))
                .expireAfterWrite(Duration.ofMinutes(10))
                .autosaveInterval(Duration.ofSeconds(20))
                .loadOnJoin(true)
                .saveOnQuit(true)
                .unloadOnQuit(true)
                .saveOnEvict(false)
                .recordStats(true)
                .build();

        assertEquals(250, settings.maximumSize());
        assertEquals(Duration.ofMinutes(5), settings.expireAfterAccess());
        assertEquals(Duration.ofMinutes(10), settings.expireAfterWrite());
        assertEquals(Duration.ofSeconds(20), settings.autosaveInterval());
        assertTrue(settings.loadOnJoin());
        assertTrue(settings.saveOnQuit());
        assertTrue(settings.unloadOnQuit());
        assertFalse(settings.saveOnEvict());
        assertTrue(settings.recordStats());
        assertTrue(settings.autosaveEnabled());
        assertTrue(settings.expireAfterAccessEnabled());
        assertTrue(settings.expireAfterWriteEnabled());
    }

    @Test
    void nullDurationsReject() {
        CacheSettingsBuilder builder = CacheSettings.builder();

        assertThrows(NullPointerException.class, () -> builder.expireAfterAccess(null));
        assertThrows(NullPointerException.class, () -> builder.expireAfterWrite(null));
        assertThrows(NullPointerException.class, () -> builder.autosaveInterval(null));
    }

    @Test
    void zeroDurationsKeepFeaturesDisabled() {
        CacheSettings settings = CacheSettings.builder()
                .expireAfterAccess(Duration.ZERO)
                .expireAfterWrite(Duration.ZERO)
                .autosaveInterval(Duration.ZERO)
                .build();

        assertFalse(settings.expireAfterAccessEnabled());
        assertFalse(settings.expireAfterWriteEnabled());
        assertFalse(settings.autosaveEnabled());
    }

    @Test
    void negativeDurationsAreTreatedAsDisabledFeatures() {
        CacheSettings settings = CacheSettings.builder()
                .expireAfterAccess(Duration.ofMinutes(-1))
                .expireAfterWrite(Duration.ofMinutes(-1))
                .autosaveInterval(Duration.ofMinutes(-1))
                .build();

        assertFalse(settings.expireAfterAccessEnabled());
        assertFalse(settings.expireAfterWriteEnabled());
        assertFalse(settings.autosaveEnabled());
    }
}
