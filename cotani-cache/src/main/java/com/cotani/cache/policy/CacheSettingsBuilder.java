package com.cotani.cache.policy;

import java.time.Duration;
import java.util.Objects;

/**
 * Fluent builder for {@link CacheSettings}.
 *
 * <p>Each method returns {@code this} for chaining. Call {@link #build()} to
 * produce the immutable settings record.
 */
public final class CacheSettingsBuilder {

    private static final String DURATION_PARAM = "duration";

    private long maximumSize = 10_000;
    private Duration expireAfterAccess = Duration.ZERO;
    private Duration expireAfterWrite = Duration.ZERO;
    private Duration autosaveInterval = Duration.ZERO;
    private boolean loadOnJoin;
    private boolean saveOnQuit;
    private boolean unloadOnQuit;
    private boolean saveOnEvict;
    private boolean recordStats;

    public CacheSettingsBuilder maximumSize(long maximumSize) {
        this.maximumSize = maximumSize;
        return this;
    }

    public CacheSettingsBuilder expireAfterAccess(Duration duration) {
        this.expireAfterAccess = Objects.requireNonNull(duration, DURATION_PARAM);
        return this;
    }

    public CacheSettingsBuilder expireAfterWrite(Duration duration) {
        this.expireAfterWrite = Objects.requireNonNull(duration, DURATION_PARAM);
        return this;
    }

    public CacheSettingsBuilder autosaveInterval(Duration duration) {
        this.autosaveInterval = Objects.requireNonNull(duration, DURATION_PARAM);
        return this;
    }

    public CacheSettingsBuilder loadOnJoin(boolean enabled) {
        this.loadOnJoin = enabled;
        return this;
    }

    public CacheSettingsBuilder saveOnQuit(boolean enabled) {
        this.saveOnQuit = enabled;
        return this;
    }

    public CacheSettingsBuilder unloadOnQuit(boolean enabled) {
        this.unloadOnQuit = enabled;
        return this;
    }

    public CacheSettingsBuilder saveOnEvict(boolean enabled) {
        this.saveOnEvict = enabled;
        return this;
    }

    public CacheSettingsBuilder recordStats(boolean enabled) {
        this.recordStats = enabled;
        return this;
    }

    public CacheSettings build() {
        return new CacheSettings(
                maximumSize,
                expireAfterAccess,
                expireAfterWrite,
                autosaveInterval,
                loadOnJoin,
                saveOnQuit,
                unloadOnQuit,
                saveOnEvict,
                recordStats);
    }
}
