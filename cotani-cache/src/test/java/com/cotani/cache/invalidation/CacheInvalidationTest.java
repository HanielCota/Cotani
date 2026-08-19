package com.cotani.cache.invalidation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class CacheInvalidationTest {
    private final UUID sourceId = UUID.randomUUID();

    @Test
    void exposesSourceIdAndKey() {
        var invalidation = new CacheInvalidation<>(sourceId, "key");

        assertEquals(sourceId, invalidation.sourceId());
        assertEquals("key", invalidation.key());
    }

    @Test
    void nullSourceIdRejects() {
        assertThrows(NullPointerException.class, () -> new CacheInvalidation<>(null, "key"));
    }

    @Test
    void nullKeyRejects() {
        assertThrows(NullPointerException.class, () -> new CacheInvalidation<>(sourceId, null));
    }

    @Test
    void valueSemanticsFollowRecordContract() {
        var first = new CacheInvalidation<>(sourceId, "key");
        var second = new CacheInvalidation<>(sourceId, "key");
        var different = new CacheInvalidation<>(UUID.randomUUID(), "key");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, different);
    }
}
