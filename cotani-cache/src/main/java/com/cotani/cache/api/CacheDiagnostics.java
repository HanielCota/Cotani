package com.cotani.cache.api;

import com.cotani.cache.stats.CacheStatsView;
import java.util.Map;

/** Exposes immutable cache snapshots and statistics without mutation capabilities. */
public interface CacheDiagnostics<K, V> {
    boolean contains(K key);

    int dirtyCount();

    long size();

    Map<K, V> snapshot();

    CacheStatsView stats();
}
