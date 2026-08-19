package com.cotani.user.internal.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.user.internal.model.SimpleCotaniUser;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class UserCacheBoundaryTest {
    @Test
    void constructorRejectsNonPositiveMaxSize() {
        assertThrows(IllegalArgumentException.class, () -> new UserCache(0));
        assertThrows(IllegalArgumentException.class, () -> new UserCache(-1));
    }

    @Test
    void evictsOldestEntryWhenMaxSizeExceeded() {
        UserCache cache = new UserCache(1);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        cache.put(SimpleCotaniUser.createNew(firstId, "A", 1L));
        cache.put(SimpleCotaniUser.createNew(secondId, "B", 1L));

        assertEquals(1, cache.allInternal().size());
        assertTrue(cache.contains(firstId) || cache.contains(secondId));
    }

    @Test
    void evictionKeepsSizeWithinBoundsForManyPuts() {
        UserCache cache = new UserCache(2);
        for (int i = 0; i < 10; i++) {
            cache.put(SimpleCotaniUser.createNew(UUID.randomUUID(), "P" + i, 1L));
        }

        assertEquals(2, cache.allInternal().size());
    }

    @Test
    void findReturnsEmptyForUnknownKey() {
        UserCache cache = new UserCache();

        assertTrue(cache.findInternal(UUID.randomUUID()).isEmpty());
        assertTrue(cache.find(UUID.randomUUID()).isEmpty());
        assertFalse(cache.contains(UUID.randomUUID()));
    }

    @Test
    void updateIfSessionReturnsEmptyWhenKeyIsAbsent() {
        UserCache cache = new UserCache();

        Optional<SimpleCotaniUser> updated = cache.updateIfSession(UUID.randomUUID(), UUID.randomUUID(), user -> user);

        assertTrue(updated.isEmpty());
    }

    @Test
    void updateIfSessionAppliesUpdaterAndStoresResult() {
        UserCache cache = new UserCache();
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);
        cache.put(user);

        Optional<SimpleCotaniUser> updated =
                cache.updateIfSession(uniqueId, user.sessionId(), current -> current.withLastQuitAt(9_000L));

        assertTrue(updated.isPresent());
        assertEquals(9_000L, updated.orElseThrow().lastQuitAt());
        assertEquals(9_000L, cache.findInternal(uniqueId).orElseThrow().lastQuitAt());
    }

    @Test
    void updateIfSessionRejectsUpdaterReturningNull() {
        UserCache cache = new UserCache();
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);
        cache.put(user);

        assertThrows(
                NullPointerException.class, () -> cache.updateIfSession(uniqueId, user.sessionId(), current -> null));
    }

    @Test
    void putRejectsNullUser() {
        UserCache cache = new UserCache();

        assertThrows(NullPointerException.class, () -> cache.put(null));
    }

    @Test
    void findRejectsNullUniqueId() {
        UserCache cache = new UserCache();

        assertThrows(NullPointerException.class, () -> cache.findInternal(null));
        assertThrows(NullPointerException.class, () -> cache.find(null));
        assertThrows(NullPointerException.class, () -> cache.contains(null));
        assertThrows(NullPointerException.class, () -> cache.remove(null, UUID.randomUUID()));
    }

    @Test
    void removeRejectsNullSessionId() {
        UserCache cache = new UserCache();

        assertThrows(NullPointerException.class, () -> cache.remove(UUID.randomUUID(), null));
    }

    @Test
    void updateIfSessionRejectsNullArguments() {
        UserCache cache = new UserCache();
        UUID uniqueId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        assertThrows(NullPointerException.class, () -> cache.updateIfSession(null, sessionId, user -> user));
        assertThrows(NullPointerException.class, () -> cache.updateIfSession(uniqueId, null, user -> user));
        assertThrows(NullPointerException.class, () -> cache.updateIfSession(uniqueId, sessionId, null));
    }

    @Test
    void defaultCacheHoldsUpToTenThousandUsers() {
        UserCache cache = new UserCache();

        for (int i = 0; i < 10_000; i++) {
            cache.put(SimpleCotaniUser.createNew(UUID.randomUUID(), "P" + i, 1L));
        }

        assertEquals(10_000, cache.allInternal().size());
    }
}
