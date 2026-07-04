package com.cotani.cache.policy;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for cache behavior.
 *
 * @param maximumSize       maximum number of entries
 * @param expireAfterAccess duration after which an entry expires since last access
 * @param expireAfterWrite  duration after which an entry expires since creation/update
 * @param autosaveInterval  interval between automatic dirty saves
 * @param loadOnJoin        whether to load data when a player joins
 * @param saveOnQuit        whether to save data when a player quits
 * @param unloadOnQuit      whether to unload data when a player quits
 * @param saveOnEvict       whether to save dirty data when evicted
 * @param recordStats       whether to record cache statistics
 */
public record CacheSettings(
        long maximumSize,
        Duration expireAfterAccess,
        Duration expireAfterWrite,
        Duration autosaveInterval,
        boolean loadOnJoin,
        boolean saveOnQuit,
        boolean unloadOnQuit,
        boolean saveOnEvict,
        boolean recordStats) {

    public CacheSettings {
        Objects.requireNonNull(expireAfterAccess, "expireAfterAccess");
        Objects.requireNonNull(expireAfterWrite, "expireAfterWrite");
        Objects.requireNonNull(autosaveInterval, "autosaveInterval");
    }

    public static CacheSettingsBuilder builder() {
        return new CacheSettingsBuilder();
    }

    public static CacheSettings playerData() {
        return new CacheSettings(
                10_000, Duration.ofMinutes(30), Duration.ZERO, Duration.ofMinutes(5), true, true, true, true, true);
    }

    public static CacheSettings temporary() {
        return new CacheSettings(
                10_000, Duration.ofMinutes(10), Duration.ZERO, Duration.ZERO, false, false, false, false, true);
    }

    public static CacheSettings staticData() {
        return new CacheSettings(
                10_000, Duration.ZERO, Duration.ofHours(1), Duration.ZERO, false, false, false, false, true);
    }

    public static CacheSettings highActivity() {
        return new CacheSettings(
                50_000, Duration.ofMinutes(30), Duration.ZERO, Duration.ofMinutes(1), true, true, true, true, true);
    }

    private static boolean isPositive(Duration duration) {
        return !duration.isZero() && !duration.isNegative();
    }

    public boolean autosaveEnabled() {
        return isPositive(autosaveInterval);
    }

    public boolean expireAfterAccessEnabled() {
        return isPositive(expireAfterAccess);
    }

    public boolean expireAfterWriteEnabled() {
        return isPositive(expireAfterWrite);
    }
}
