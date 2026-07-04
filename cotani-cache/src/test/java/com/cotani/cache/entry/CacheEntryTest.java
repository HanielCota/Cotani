package com.cotani.cache.entry;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class CacheEntryTest {

    @Test
    void valueReturnsInitialValue() {
        CacheEntry<String> entry = new CacheEntry<>("hello");

        assertEquals("hello", entry.value());
    }

    @Test
    void constructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new CacheEntry<>(null));
    }

    @Test
    void updateReturnsUpdatedValue() {
        CacheEntry<String> entry = new CacheEntry<>("old");

        String updated = entry.update(v -> v + "-new");

        assertEquals("old-new", updated);
        assertEquals("old-new", entry.value());
    }

    @Test
    void updateRejectsNullResult() {
        CacheEntry<String> entry = new CacheEntry<>("value");

        assertThrows(NullPointerException.class, () -> entry.update(v -> null));
    }

    @Test
    void updateRejectsNullUpdater() {
        CacheEntry<String> entry = new CacheEntry<>("value");

        assertThrows(NullPointerException.class, () -> entry.update(null));
    }

    @Test
    void mutateReturnsSameReference() {
        CacheEntry<StringBuilder> entry = new CacheEntry<>(new StringBuilder("hello"));

        StringBuilder mutated = entry.mutate(sb -> sb.append(" world"));

        assertEquals("hello world", mutated.toString());
        assertEquals("hello world", entry.value().toString());
    }

    @Test
    void mutateRejectsNullMutator() {
        CacheEntry<String> entry = new CacheEntry<>("value");

        assertThrows(NullPointerException.class, () -> entry.mutate(null));
    }

    @Test
    void dirtyReturnsFalseInitially() {
        CacheEntry<String> entry = new CacheEntry<>("value");

        assertFalse(entry.dirty());
    }

    @Test
    void markDirtySetsDirtyFlag() {
        CacheEntry<String> entry = new CacheEntry<>("value");

        entry.markDirty();

        assertTrue(entry.dirty());
    }

    @Test
    void updateSetsDirtyFlag() {
        CacheEntry<String> entry = new CacheEntry<>("value");

        entry.update(v -> v + "-updated");

        assertTrue(entry.dirty());
    }

    @Test
    void mutateSetsDirtyFlag() {
        CacheEntry<StringBuilder> entry = new CacheEntry<>(new StringBuilder("hello"));

        entry.mutate(sb -> sb.append(" world"));

        assertTrue(entry.dirty());
    }

    @Test
    void markSavedClearsDirtyFlag() {
        CacheEntry<String> entry = new CacheEntry<>("value");

        entry.markDirty();
        assertTrue(entry.dirty());

        entry.markSaved();
        assertFalse(entry.dirty());
    }

    @Test
    void loadedAtIsSetOnCreation() {
        Instant before = Instant.now();
        CacheEntry<String> entry = new CacheEntry<>("value");
        Instant after = Instant.now();

        assertFalse(entry.loadedAt().isBefore(before));
        assertFalse(entry.loadedAt().isAfter(after));
    }

    @Test
    void lastSavedAtIsEmptyInitially() {
        CacheEntry<String> entry = new CacheEntry<>("value");

        Optional<Instant> lastSaved = entry.lastSavedAt();

        assertTrue(lastSaved.isEmpty());
    }

    @Test
    void lastSavedAtIsPresentAfterMarkSaved() {
        CacheEntry<String> entry = new CacheEntry<>("value");

        entry.markSaved();
        Optional<Instant> lastSaved = entry.lastSavedAt();

        assertTrue(lastSaved.isPresent());
    }

    @Test
    void dirtyFlagSurvivesMultipleUpdates() {
        CacheEntry<String> entry = new CacheEntry<>("a");

        entry.update(v -> v + "b");
        entry.update(v -> v + "c");

        assertTrue(entry.dirty());
        assertEquals("abc", entry.value());
    }
}
