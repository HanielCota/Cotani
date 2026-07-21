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
    void updateReturnsBecameDirty() {
        CacheEntry<String> entry = new CacheEntry<>("old");

        assertTrue(entry.update(v -> v + "-new"));
        assertEquals("old-new", entry.value());
    }

    @Test
    void updateOnAlreadyDirtyEntryDoesNotReportBecameDirty() {
        CacheEntry<String> entry = new CacheEntry<>("value");
        entry.markDirty();

        assertFalse(entry.update(v -> v + "-new"));
        assertTrue(entry.dirty());
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
    void mutateReturnsBecameDirty() {
        CacheEntry<StringBuilder> entry = new CacheEntry<>(new StringBuilder("hello"));

        assertTrue(entry.mutate(sb -> sb.append(" world")));
        assertEquals("hello world", entry.value().toString());
    }

    @Test
    void mutateOnAlreadyDirtyEntryDoesNotReportBecameDirty() {
        CacheEntry<StringBuilder> entry = new CacheEntry<>(new StringBuilder("hello"));
        entry.markDirty();

        assertFalse(entry.mutate(sb -> sb.append(" world")));
        assertTrue(entry.dirty());
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
    void markDirtySetsDirtyFlagAndReturnsBecameDirty() {
        CacheEntry<String> entry = new CacheEntry<>("value");

        assertTrue(entry.markDirty());
        assertTrue(entry.dirty());
    }

    @Test
    void markDirtyOnAlreadyDirtyEntryDoesNotReportBecameDirty() {
        CacheEntry<String> entry = new CacheEntry<>("value");
        entry.markDirty();

        assertFalse(entry.markDirty());
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

        assertTrue(entry.update(v -> v + "b"));
        assertFalse(entry.update(v -> v + "c"));

        assertTrue(entry.dirty());
        assertEquals("abc", entry.value());
    }

    @Test
    void versionIncrementsOnUpdateAndMarkDirty() {
        CacheEntry<String> entry = new CacheEntry<>("value");

        assertEquals(0L, entry.version());

        entry.update(v -> v + "-1");
        assertEquals(1L, entry.version());

        entry.markDirty();
        assertEquals(2L, entry.version());
    }

    @Test
    void markSavedIfVersionMatchesClearsDirtyWhenVersionMatches() {
        CacheEntry<String> entry = new CacheEntry<>("value");
        entry.markDirty();
        long versionAtStart = entry.version();

        assertTrue(entry.markSavedIfVersionMatches(versionAtStart));
        assertFalse(entry.dirty());
    }

    @Test
    void markSavedIfVersionMatchesReturnsFalseWhenVersionChanged() {
        CacheEntry<String> entry = new CacheEntry<>("value");
        entry.markDirty();
        long versionAtStart = entry.version();

        entry.update(v -> v + "-new");

        assertFalse(entry.markSavedIfVersionMatches(versionAtStart));
        assertTrue(entry.dirty());
    }

    @Test
    void markSavedIfVersionMatchesReturnsFalseWhenAlreadyClean() {
        CacheEntry<String> entry = new CacheEntry<>("value");

        assertFalse(entry.markSavedIfVersionMatches(entry.version()));
    }
}
