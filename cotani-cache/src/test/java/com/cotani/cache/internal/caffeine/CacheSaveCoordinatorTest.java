package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.cache.invalidation.NoopCacheInvalidationBus;
import com.cotani.cache.repository.CacheRepository;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
