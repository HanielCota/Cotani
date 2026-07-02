package com.cotani.cache.policy;

import java.time.Duration;

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

    public boolean autosaveEnabled() {
        return isPositive(autosaveInterval);
    }

    public boolean expireAfterAccessEnabled() {
        return isPositive(expireAfterAccess);
    }

    public boolean expireAfterWriteEnabled() {
        return isPositive(expireAfterWrite);
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
