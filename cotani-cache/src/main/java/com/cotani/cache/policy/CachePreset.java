package com.cotani.cache.policy;

public enum CachePreset {
    PLAYER_DATA,
    TEMPORARY,
    STATIC_DATA,
    HIGH_ACTIVITY;

    public CacheSettings settings() {
        return switch (this) {
            case PLAYER_DATA -> CacheSettings.playerData();
            case TEMPORARY -> CacheSettings.temporary();
            case STATIC_DATA -> CacheSettings.staticData();
            case HIGH_ACTIVITY -> CacheSettings.highActivity();
        };
    }
}
