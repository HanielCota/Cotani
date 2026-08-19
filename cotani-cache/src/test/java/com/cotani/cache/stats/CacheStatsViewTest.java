package com.cotani.cache.stats;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class CacheStatsViewTest {
    @Test
    void exposesAllRecordedValues() {
        var stats = new CacheStatsView(10, 7, 3, 0.7, 2, 1);

        assertEquals(10, stats.size());
        assertEquals(7, stats.hitCount());
        assertEquals(3, stats.missCount());
        assertEquals(0.7, stats.hitRate());
        assertEquals(2, stats.evictionCount());
        assertEquals(1, stats.dirtyEntries());
    }

    @Test
    void zeroCountersRepresentFreshCache() {
        var stats = new CacheStatsView(0, 0, 0, 0.0, 0, 0);

        assertEquals(0, stats.size());
        assertEquals(0L, stats.hitCount());
        assertEquals(0L, stats.missCount());
        assertEquals(0.0, stats.hitRate());
        assertEquals(0L, stats.evictionCount());
        assertEquals(0, stats.dirtyEntries());
    }

    @Test
    void valueSemanticsFollowRecordContract() {
        var first = new CacheStatsView(1, 2, 3, 0.5, 4, 5);
        var second = new CacheStatsView(1, 2, 3, 0.5, 4, 5);
        var different = new CacheStatsView(1, 2, 3, 0.6, 4, 5);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, different);
        assertTrue(first.toString().contains("hitRate=0.5"));
    }
}
