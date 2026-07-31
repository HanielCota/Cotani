package com.cotani.user.internal.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cotani.user.api.CotaniUser;
import com.cotani.user.internal.model.SimpleCotaniUser;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class UserCacheTest {
    private final UserCache cache = new UserCache();

    @Test
    void putAndFindReturnUser() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);

        cache.put(user);

        Optional<SimpleCotaniUser> internal = cache.findInternal(uniqueId);
        Optional<CotaniUser> exposed = cache.find(uniqueId);

        assertTrue(internal.isPresent());
        assertEquals(user.uniqueId(), internal.get().uniqueId());
        assertTrue(cache.contains(uniqueId));
        assertTrue(exposed.isPresent());
        assertEquals(user.uniqueId(), exposed.get().uniqueId());
    }

    @Test
    void removeOnlyWithMatchingSessionId() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);
        cache.put(user);

        assertFalse(cache.remove(uniqueId, UUID.randomUUID()));
        assertTrue(cache.contains(uniqueId));

        assertTrue(cache.remove(uniqueId, user.sessionId()));
        assertFalse(cache.contains(uniqueId));
    }

    @Test
    void clearRemovesAllEntries() {
        cache.put(SimpleCotaniUser.createNew(UUID.randomUUID(), "A", 1L));
        cache.put(SimpleCotaniUser.createNew(UUID.randomUUID(), "B", 1L));

        cache.clear();

        assertTrue(cache.allInternal().isEmpty());
    }

    @Test
    void allInternalReturnsSnapshotOfCachedUsers() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        cache.put(SimpleCotaniUser.createNew(firstId, "A", 1L));
        cache.put(SimpleCotaniUser.createNew(secondId, "B", 1L));

        assertEquals(2, cache.allInternal().size());
    }

    @Test
    void concurrentRemoveWithSameSessionIdOnlyOneSucceeds() throws InterruptedException {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);
        cache.put(user);

        int threads = 10;
        var results = new ConcurrentLinkedQueue<Boolean>();
        var latch = new CountDownLatch(threads);
        var executor = Executors.newFixedThreadPool(threads);

        try {
            for (int i = 0; i < threads; i++) {
                var _ = executor.submit(() -> {
                    try {
                        results.add(cache.remove(uniqueId, user.sessionId()));
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
        }

        long successCount = results.stream().filter(Boolean::booleanValue).count();
        assertEquals(1, successCount);
        assertFalse(cache.contains(uniqueId));
    }

    @Test
    void oldSessionUpdateCannotOverwriteReconnectedSession() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser oldSession = SimpleCotaniUser.createNew(uniqueId, "Steve", 1L);
        cache.put(oldSession);
        SimpleCotaniUser newSession = oldSession.withNewSessionId();
        cache.put(newSession);

        Optional<SimpleCotaniUser> updated =
                cache.updateIfSession(uniqueId, oldSession.sessionId(), user -> user.withLastQuitAt(2L));

        assertTrue(updated.isEmpty());
        assertEquals(newSession, cache.findInternal(uniqueId).orElseThrow());
    }
}
