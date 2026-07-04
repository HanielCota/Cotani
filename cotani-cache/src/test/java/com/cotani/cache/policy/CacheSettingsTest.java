package com.cotani.cache.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class CacheSettingsTest {

    @Test
    void playerDataHasExpectedDefaults() {
        CacheSettings settings = CacheSettings.playerData();

        assertEquals(10_000, settings.maximumSize());
        assertEquals(Duration.ofMinutes(30), settings.expireAfterAccess());
        assertEquals(Duration.ZERO, settings.expireAfterWrite());
        assertEquals(Duration.ofMinutes(5), settings.autosaveInterval());
        assertTrue(settings.loadOnJoin());
        assertTrue(settings.saveOnQuit());
        assertTrue(settings.unloadOnQuit());
        assertTrue(settings.saveOnEvict());
        assertTrue(settings.recordStats());
    }

    @Test
    void temporaryHasExpectedDefaults() {
        CacheSettings settings = CacheSettings.temporary();

        assertEquals(10_000, settings.maximumSize());
        assertEquals(Duration.ofMinutes(10), settings.expireAfterAccess());
        assertEquals(Duration.ZERO, settings.expireAfterWrite());
        assertEquals(Duration.ZERO, settings.autosaveInterval());
        assertFalse(settings.loadOnJoin());
        assertFalse(settings.saveOnQuit());
        assertFalse(settings.unloadOnQuit());
        assertFalse(settings.saveOnEvict());
        assertTrue(settings.recordStats());
    }

    @Test
    void staticDataHasExpectedDefaults() {
        CacheSettings settings = CacheSettings.staticData();

        assertEquals(10_000, settings.maximumSize());
        assertEquals(Duration.ZERO, settings.expireAfterAccess());
        assertEquals(Duration.ofHours(1), settings.expireAfterWrite());
        assertEquals(Duration.ZERO, settings.autosaveInterval());
        assertFalse(settings.loadOnJoin());
        assertFalse(settings.saveOnQuit());
        assertFalse(settings.unloadOnQuit());
        assertFalse(settings.saveOnEvict());
        assertTrue(settings.recordStats());
    }

    @Test
    void highActivityHasExpectedDefaults() {
        CacheSettings settings = CacheSettings.highActivity();

        assertEquals(50_000, settings.maximumSize());
        assertEquals(Duration.ofMinutes(30), settings.expireAfterAccess());
        assertEquals(Duration.ZERO, settings.expireAfterWrite());
        assertEquals(Duration.ofMinutes(1), settings.autosaveInterval());
        assertTrue(settings.loadOnJoin());
        assertTrue(settings.saveOnQuit());
        assertTrue(settings.unloadOnQuit());
        assertTrue(settings.saveOnEvict());
        assertTrue(settings.recordStats());
    }

    @Test
    void autosaveEnabledReturnsTrueForPositiveInterval() {
        CacheSettings settings = CacheSettings.playerData();

        assertTrue(settings.autosaveEnabled());
    }

    @Test
    void autosaveEnabledReturnsFalseForZeroInterval() {
        CacheSettings settings = CacheSettings.temporary();

        assertFalse(settings.autosaveEnabled());
    }

    @Test
    void expireAfterAccessEnabledReturnsTrueForPositiveDuration() {
        CacheSettings settings = CacheSettings.temporary();

        assertTrue(settings.expireAfterAccessEnabled());
    }

    @Test
    void expireAfterAccessEnabledReturnsFalseForZeroDuration() {
        CacheSettings settings = CacheSettings.staticData();

        assertFalse(settings.expireAfterAccessEnabled());
    }

    @Test
    void expireAfterWriteEnabledReturnsTrueForPositiveDuration() {
        CacheSettings settings = CacheSettings.staticData();

        assertTrue(settings.expireAfterWriteEnabled());
    }

    @Test
    void expireAfterWriteEnabledReturnsFalseForZeroDuration() {
        CacheSettings settings = CacheSettings.temporary();

        assertFalse(settings.expireAfterWriteEnabled());
    }

    @Test
    void builderCreatesSettings() {
        CacheSettings settings = CacheSettings.builder()
                .maximumSize(500)
                .expireAfterAccess(Duration.ofMinutes(15))
                .expireAfterWrite(Duration.ofHours(2))
                .autosaveInterval(Duration.ofSeconds(30))
                .loadOnJoin(true)
                .saveOnQuit(true)
                .unloadOnQuit(false)
                .saveOnEvict(true)
                .recordStats(true)
                .build();

        assertEquals(500, settings.maximumSize());
        assertEquals(Duration.ofMinutes(15), settings.expireAfterAccess());
        assertEquals(Duration.ofHours(2), settings.expireAfterWrite());
        assertEquals(Duration.ofSeconds(30), settings.autosaveInterval());
        assertTrue(settings.loadOnJoin());
        assertTrue(settings.saveOnQuit());
        assertFalse(settings.unloadOnQuit());
        assertTrue(settings.saveOnEvict());
        assertTrue(settings.recordStats());
    }

    @Test
    void recordConstructorRejectsNullDuration() {
        assertThrows(
                NullPointerException.class,
                () -> new CacheSettings(100, null, Duration.ZERO, Duration.ZERO, false, false, false, false, false));
        assertThrows(
                NullPointerException.class,
                () -> new CacheSettings(100, Duration.ZERO, null, Duration.ZERO, false, false, false, false, false));
        assertThrows(
                NullPointerException.class,
                () -> new CacheSettings(100, Duration.ZERO, Duration.ZERO, null, false, false, false, false, false));
    }
}
