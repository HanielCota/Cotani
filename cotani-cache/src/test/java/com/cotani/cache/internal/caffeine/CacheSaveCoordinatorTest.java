package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.cache.invalidation.CacheInvalidation;
import com.cotani.cache.invalidation.LocalCacheInvalidationBus;
import com.cotani.cache.invalidation.NoopCacheInvalidationBus;
import com.cotani.cache.repository.CacheRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CacheSaveCoordinatorTest {
    @Test
    void serializesSavesForTheSameKey() {
        CacheRepository<String, String> repository = repository();
        var firstRepositorySave = new CompletableFuture<Void>();
        var secondRepositorySave = new CompletableFuture<Void>();
        when(repository.save("key", "first")).thenReturn(firstRepositorySave);
        when(repository.save("key", "second")).thenReturn(secondRepositorySave);
        var coordinator = coordinator(repository, 4);

        var first = coordinator.persist("key", "first", new SaveOrder(1, 1));
        var second = coordinator.persist("key", "second", new SaveOrder(2, 1));

        verify(repository).save("key", "first");
        verify(repository, never()).save("key", "second");
        assertFalse(second.toCompletableFuture().isDone());

        firstRepositorySave.complete(null);

        verify(repository).save("key", "second");
        assertTrue(first.toCompletableFuture().isDone());
        secondRepositorySave.complete(null);
        assertTrue(second.toCompletableFuture().isDone());
    }

    @Test
    void obsoleteSaveCannotOverwriteNewerGeneration() {
        CacheRepository<String, String> repository = repository();
        var newestRepositorySave = new CompletableFuture<Void>();
        when(repository.save("key", "newest")).thenReturn(newestRepositorySave);
        var coordinator = coordinator(repository, 4);

        var newest = coordinator.persist("key", "newest", new SaveOrder(2, 1));
        var obsolete = coordinator.persist("key", "obsolete", new SaveOrder(1, 99));
        newestRepositorySave.complete(null);

        assertTrue(newest.toCompletableFuture().isDone());
        assertTrue(obsolete.toCompletableFuture().isDone());
        verify(repository, never()).save("key", "obsolete");
    }

    @Test
    void failedEvictionIsQueuedForShutdownRetry() {
        CacheRepository<String, String> repository = repository();
        when(repository.save("key", "value"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("first save failed")))
                .thenReturn(CompletableFuture.completedFuture(null));
        var coordinator = coordinator(repository, 2);

        coordinator.saveEvicted("key", "value", new SaveOrder(1, 1));
        var closeWork = coordinator
                .awaitEvictionWork()
                .thenCompose(_ -> coordinator.savePending())
                .thenCompose(_ -> coordinator.awaitSaveLanes());

        assertTrue(closeWork.toCompletableFuture().isDone());
        assertFalse(closeWork.toCompletableFuture().isCompletedExceptionally());
        verify(repository, times(2)).save("key", "value");
    }

    @Test
    void persistPublishesInvalidationAfterSave() {
        CacheRepository<String, String> repository = repository();
        var bus = new LocalCacheInvalidationBus<String>();
        var cacheId = UUID.randomUUID();
        var coordinator = new CacheSaveCoordinator<>(repository, bus, cacheId, 4);
        var received = new ArrayList<CacheInvalidation<String>>();
        bus.subscribe(received::add);
        when(repository.save("key", "value")).thenReturn(CompletableFuture.completedFuture(null));

        coordinator
                .persist("key", "value", new SaveOrder(1, 1))
                .toCompletableFuture()
                .join();

        assertEquals(1, received.size());
        assertEquals(cacheId, received.get(0).sourceId());
        assertEquals("key", received.get(0).key());
    }

    @Test
    void persistFailsWhenRepositorySaveReturnsNull() {
        CacheRepository<String, String> repository = repository();
        when(repository.save("key", "value")).thenReturn(null);
        var coordinator = coordinator(repository, 4);

        var stage = coordinator.persist("key", "value", new SaveOrder(1, 1));

        var thrown = assertThrows(
                ExecutionException.class, () -> stage.toCompletableFuture().get());

        assertInstanceOf(NullPointerException.class, rootCause(thrown));
    }

    @Test
    void savePendingPersistsOnlyNewestQueuedValue() {
        CacheRepository<String, String> repository = repository();
        when(repository.save("key", "newest")).thenReturn(CompletableFuture.completedFuture(null));
        var coordinator = coordinator(repository, 4);

        coordinator.queue("key", "older", new SaveOrder(1, 1));
        coordinator.queue("key", "newest", new SaveOrder(2, 1));
        coordinator.savePending().toCompletableFuture().join();

        verify(repository).save("key", "newest");
        verify(repository, never()).save("key", "older");
    }

    @Test
    void savePendingDrainsQueue() {
        CacheRepository<String, String> repository = repository();
        when(repository.save(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        var coordinator = coordinator(repository, 4);

        coordinator.queue("key", "value", new SaveOrder(1, 1));
        coordinator.savePending().toCompletableFuture().join();
        coordinator.savePending().toCompletableFuture().join();

        verify(repository, times(1)).save("key", "value");
    }

    @Test
    void savePendingWithNothingQueuedCompletesImmediately() {
        CacheRepository<String, String> repository = repository();
        var coordinator = coordinator(repository, 4);

        var stage = coordinator.savePending();

        assertTrue(stage.toCompletableFuture().isDone());
        verify(repository, never()).save(anyString(), anyString());
    }

    @Test
    void runBoundedLimitsConcurrentWork() {
        CacheRepository<String, String> repository = repository();
        var coordinator = coordinator(repository, 2);
        var gateA = new CompletableFuture<Void>();
        var gateB = new CompletableFuture<Void>();
        var started = new AtomicInteger();

        var stage = coordinator.runBounded(List.of("a", "b", "c", "d"), item -> {
            started.incrementAndGet();
            return switch (item) {
                case "a" -> gateA;
                case "b" -> gateB;
                default -> CompletableFuture.completedFuture(null);
            };
        });

        assertEquals(2, started.get());
        assertFalse(stage.toCompletableFuture().isDone());

        gateA.complete(null);
        assertEquals(4, started.get());
        gateB.complete(null);
        assertTrue(stage.toCompletableFuture().isDone());
    }

    @Test
    void runBoundedWithNoItemsCompletesImmediately() {
        CacheRepository<String, String> repository = repository();
        var coordinator = coordinator(repository, 4);

        var stage = coordinator.runBounded(List.of(), item -> {
            fail("no work expected");

            return null;
        });

        assertTrue(stage.toCompletableFuture().isDone());
    }

    @Test
    void constructorRejectsNonPositiveConcurrency() {
        CacheRepository<String, String> repository = repository();

        assertThrows(
                IllegalArgumentException.class,
                () -> new CacheSaveCoordinator<>(repository, new NoopCacheInvalidationBus<>(), UUID.randomUUID(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CacheSaveCoordinator<>(repository, new NoopCacheInvalidationBus<>(), UUID.randomUUID(), -1));
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable root = failure;

        while (root.getCause() != null) {
            root = root.getCause();
        }

        return root;
    }

    @SuppressWarnings("unchecked")
    private static CacheRepository<String, String> repository() {
        return Mockito.mock(CacheRepository.class);
    }

    private static CacheSaveCoordinator<String, String> coordinator(
            CacheRepository<String, String> repository, int maximumConcurrency) {
        return new CacheSaveCoordinator<>(
                repository, new NoopCacheInvalidationBus<>(), UUID.randomUUID(), maximumConcurrency);
    }
}
