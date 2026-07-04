package com.cotani.cache.stats;

/**
 * Immutable snapshot of cache statistics at a point in time.
 *
 * @param size         estimated number of entries in the cache
 * @param hitCount     total number of cache hits
 * @param missCount    total number of cache misses
 * @param hitRate      ratio of hits to total requests (0.0 to 1.0)
 * @param evictionCount total number of entries evicted
 * @param dirtyEntries number of entries pending save
 */
public record CacheStatsView(
        long size, long hitCount, long missCount, double hitRate, long evictionCount, int dirtyEntries) {}
