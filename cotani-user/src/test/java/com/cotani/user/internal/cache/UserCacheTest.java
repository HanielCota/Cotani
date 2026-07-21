package com.cotani.user.internal.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cotani.user.api.CotaniUser;
import com.cotani.user.internal.model.SimpleCotaniUser;
import com.cotani.user.internal.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class UserCacheTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final UserCache cache = new UserCache(repository);

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
    void saveIncrementsVersionAndUpdatesCachedValue() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser user =
                SimpleCotaniUser.createNew(uniqueId, "Steve", 1L).withVersion(3L);
        cache.put(user);

        when(repository.save(any(SimpleCotaniUser.class))).thenReturn(CompletableFuture.completedFuture(null));

        cache.save(uniqueId).toCompletableFuture().join();

        verify(repository).save(argThat(u -> u.version() == 4L));
        SimpleCotaniUser current = cache.findInternal(uniqueId).orElseThrow();
        assertEquals(4L, current.version());
    }

    @Test
    void saveDoesNotDowngradeVersionIfValueChangedDuringSave() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser original =
                SimpleCotaniUser.createNew(uniqueId, "Steve", 1L).withVersion(3L);
        cache.put(original);

        SimpleCotaniUser newer = original.withVersion(7L);
        cache.put(newer);

        when(repository.save(any(SimpleCotaniUser.class))).thenReturn(CompletableFuture.completedFuture(null));

        cache.save(uniqueId).toCompletableFuture().join();

        SimpleCotaniUser current = cache.findInternal(uniqueId).orElseThrow();
        assertEquals(8L, current.version());
    }

    @Test
    void saveAllSavesAllCachedUsers() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        cache.put(SimpleCotaniUser.createNew(firstId, "A", 1L).withVersion(1L));
        cache.put(SimpleCotaniUser.createNew(secondId, "B", 1L).withVersion(2L));

        when(repository.saveAll(anyList())).thenReturn(CompletableFuture.completedFuture(null));

        cache.saveAll().toCompletableFuture().join();

        verify(repository).saveAll(argThat(users -> users.size() == 2));
    }

    @Test
    void clearRemovesAllEntries() {
        cache.put(SimpleCotaniUser.createNew(UUID.randomUUID(), "A", 1L));
        cache.put(SimpleCotaniUser.createNew(UUID.randomUUID(), "B", 1L));

        cache.clear();

        assertTrue(cache.allInternal().isEmpty());
    }

    @Test
    void saveReturnsCompletedWhenUserNotPresent() {
        UUID uniqueId = UUID.randomUUID();

        assertDoesNotThrow(() -> cache.save(uniqueId).toCompletableFuture().join());
        verifyNoInteractions(repository);
    }

    @Test
    void saveAllEmptyReturnsCompletedWithoutCallingRepository() {
        assertDoesNotThrow(() -> cache.saveAll().toCompletableFuture().join());
        verifyNoInteractions(repository);
    }

    @Test
    void saveAllEmptyReturnsCompletedStage() {
        var stage = cache.saveAll();

        assertTrue(stage.toCompletableFuture().isDone());
        assertFalse(stage.toCompletableFuture().isCompletedExceptionally());
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
    void saveDoesNotOverwriteNewerValuePutDuringSave() {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser original =
                SimpleCotaniUser.createNew(uniqueId, "Steve", 1L).withVersion(1L);
        cache.put(original);

        var savePending = new CompletableFuture<Void>();
        when(repository.save(any(SimpleCotaniUser.class))).thenReturn(savePending);

        var saveFuture = cache.save(uniqueId).toCompletableFuture();
        assertFalse(saveFuture.isDone());

        SimpleCotaniUser newer = original.withVersion(5L);
        cache.put(newer);

        savePending.complete(null);
        saveFuture.join();

        SimpleCotaniUser current = cache.findInternal(uniqueId).orElseThrow();
        assertEquals(5L, current.version());
    }

    @Test
    void concurrentSaveOnSameUserLeavesCacheConsistent() throws Exception {
        UUID uniqueId = UUID.randomUUID();
        SimpleCotaniUser original =
                SimpleCotaniUser.createNew(uniqueId, "Steve", 1L).withVersion(1L);
        cache.put(original);

        var firstSave = new CompletableFuture<Void>();
        var secondSave = new CompletableFuture<Void>();
        when(repository.save(any(SimpleCotaniUser.class))).thenReturn(firstSave).thenReturn(secondSave);

        List<Future<?>> pending = new ArrayList<>();
        pending.add(cache.save(uniqueId).toCompletableFuture());
        pending.add(cache.save(uniqueId).toCompletableFuture());

        firstSave.complete(null);
        secondSave.complete(null);

        for (Future<?> future : pending) {
            future.get();
        }

        SimpleCotaniUser current = cache.findInternal(uniqueId).orElseThrow();
        assertTrue(current.version() >= 2L, "version should be incremented at least once");
    }
}
