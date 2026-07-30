package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
