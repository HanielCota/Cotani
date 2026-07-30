package com.cotani.cache.internal.caffeine;

import com.cotani.cache.entry.CacheEntry;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

final class DirtyEntryTracker<K, V> {

    private final ConcurrentHashMap<K, CacheEntry<V>> dirtyEntries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheEntry<V>, Long> entryGenerations = new ConcurrentHashMap<>();
    private final AtomicLong nextGeneration = new AtomicLong();

    CacheEntry<V> createEntry(V value) {
        var entry = CacheEntry.of(value);
        entryGenerations.put(entry, nextGeneration.incrementAndGet());
        return entry;
    }

    long generationOf(CacheEntry<V> entry) {
        return entryGenerations.computeIfAbsent(entry, _ -> nextGeneration.incrementAndGet());
    }

    void markDirty(K key, CacheEntry<V> entry) {
        dirtyEntries.put(key, entry);
    }

    void markClean(K key, CacheEntry<V> entry) {
        dirtyEntries.remove(key, entry);
    }

    void forget(CacheEntry<V> entry) {
        entryGenerations.remove(entry);
    }

    List<K> dirtyKeys() {
        return List.copyOf(dirtyEntries.keySet());
    }

    int dirtyCount() {
        return dirtyEntries.size();
    }

    void clearGenerations() {
        entryGenerations.clear();
    }
}
