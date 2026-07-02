package com.cotani.cache.stats;

public record CacheStatsView(
        long size, long hitCount, long missCount, double hitRate, long evictionCount, int dirtyEntries) {}
