package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class DirtyEntryTrackerTest {
    @Test
    void oldEntryCannotClearDirtyStateOfItsReplacement() {
        var tracker = new DirtyEntryTracker<String, String>();
        var oldEntry = tracker.createEntry("old");
        var newEntry = tracker.createEntry("new");

        tracker.markDirty("key", oldEntry);
        tracker.markDirty("key", newEntry);
        tracker.markClean("key", oldEntry);

        assertEquals(1, tracker.dirtyCount());
        assertEquals(List.of("key"), tracker.dirtyKeys());
        assertTrue(tracker.generationOf(newEntry) > tracker.generationOf(oldEntry));
    }

    @Test
    void createEntryAssignsStableGeneration() {
        var tracker = new DirtyEntryTracker<String, String>();
        var entry = tracker.createEntry("value");

        long first = tracker.generationOf(entry);
        long second = tracker.generationOf(entry);

        assertEquals(first, second);
    }

    @Test
    void generationsIncreasePerEntry() {
        var tracker = new DirtyEntryTracker<String, String>();
        var first = tracker.createEntry("a");
        var second = tracker.createEntry("b");
        var third = tracker.createEntry("c");

        assertTrue(tracker.generationOf(second) > tracker.generationOf(first));
        assertTrue(tracker.generationOf(third) > tracker.generationOf(second));
    }

    @Test
    void generationOfUnknownEntryAssignsNewGeneration() {
        var tracker = new DirtyEntryTracker<String, String>();
        var entry = tracker.createEntry("value");
        tracker.forget(entry);

        long original = tracker.generationOf(entry);
        long reassigned = tracker.generationOf(entry);

        assertEquals(original, reassigned);
    }

    @Test
    void markCleanRemovesDirtyEntry() {
        var tracker = new DirtyEntryTracker<String, String>();
        var entry = tracker.createEntry("value");

        tracker.markDirty("key", entry);
        assertEquals(1, tracker.dirtyCount());

        tracker.markClean("key", entry);

        assertEquals(0, tracker.dirtyCount());
        assertTrue(tracker.dirtyKeys().isEmpty());
    }

    @Test
    void markCleanWithoutDirtyEntryIsNoop() {
        var tracker = new DirtyEntryTracker<String, String>();
        var entry = tracker.createEntry("value");

        tracker.markClean("key", entry);

        assertEquals(0, tracker.dirtyCount());
    }

    @Test
    void dirtyKeysReturnsUnmodifiableCopy() {
        var tracker = new DirtyEntryTracker<String, String>();
        var entry = tracker.createEntry("value");
        tracker.markDirty("key", entry);

        assertThrows(
                UnsupportedOperationException.class, () -> tracker.dirtyKeys().add("other"));
        assertEquals(1, tracker.dirtyCount());
    }

    @Test
    void forgetRemovesGenerationMapping() {
        var tracker = new DirtyEntryTracker<String, String>();
        var entry = tracker.createEntry("value");
        long original = tracker.generationOf(entry);

        tracker.forget(entry);

        assertTrue(tracker.generationOf(entry) > original);
    }

    @Test
    void clearGenerationsDropsAllMappings() {
        var tracker = new DirtyEntryTracker<String, String>();
        var entry = tracker.createEntry("value");
        long original = tracker.generationOf(entry);

        tracker.clearGenerations();

        assertTrue(tracker.generationOf(entry) > original);
    }
}
