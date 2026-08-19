package com.cotani.cache.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CachePresetTest {
    @Test
    void playerDataPresetMapsToPlayerDataSettings() {
        assertEquals(CacheSettings.playerData(), CachePreset.PLAYER_DATA.settings());
    }

    @Test
    void temporaryPresetMapsToTemporarySettings() {
        assertEquals(CacheSettings.temporary(), CachePreset.TEMPORARY.settings());
    }

    @Test
    void staticDataPresetMapsToStaticDataSettings() {
        assertEquals(CacheSettings.staticData(), CachePreset.STATIC_DATA.settings());
    }

    @Test
    void highActivityPresetMapsToHighActivitySettings() {
        assertEquals(CacheSettings.highActivity(), CachePreset.HIGH_ACTIVITY.settings());
    }
}
