package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.cotani.cache.api.DataCache;
import com.cotani.cache.exception.CacheLoadException;
import com.cotani.cache.exception.CacheSaveException;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("NullAway")
class CaffeineDataCacheBehaviorTest {
    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);

    @Mock
    private CacheRepository<String, String> repository;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(scheduler.asyncExecutor()).thenReturn(CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS));
        when(scheduler.asyncTimer(any(), any(), any())).thenReturn(SchedulerTask.noop());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    private DataCache<String, String> createCache() {
        return CaffeineDataCache.create(repository, key -> "default", scheduler, CacheSettings.temporary());
    }

    @Test
    void concurrentGetOrLoadForSameKeyTriggersSingleRepositoryLoad() throws Exception {
        var gate = new CompletableFuture<Optional<String>>();
        var loadStarted = new CountDownLatch(1);
        when(repository.find(anyString())).thenAnswer(_ -> {
            loadStarted.countDown();
            return gate;
        });
        DataCache<String, String> cache = createCache();

        var first = cache.getOrLoad("key").toCompletableFuture();
        var second = cache.getOrLoad("key").toCompletableFuture();

        assertTrue(loadStarted.await(5, TimeUnit.SECONDS));
        verify(repository, times(1)).find("key");

        gate.complete(Optional.of("shared"));
        assertEquals("shared", first.get(5, TimeUnit.SECONDS));
        assertEquals("shared", second.get(5, TimeUnit.SECONDS));
        verify(repository, times(1)).find("key");
    }

    @Test
    void failedGetOrLoadPropagatesCacheLoadExceptionAndIsRetriedOnNextCall() throws Exception {
        when(repository.find(anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db down")))
                .thenReturn(CompletableFuture.completedFuture(Optional.of("recovered")));
        DataCache<String, String> cache = createCache();

        var firstFailure = assertThrows(
                ExecutionException.class,
                () -> cache.getOrLoad("key").toCompletableFuture().get(5, TimeUnit.SECONDS));

        assertInstanceOf(CacheLoadException.class, findCause(firstFailure, CacheLoadException.class));
        assertFalse(cache.contains("key"));

        assertEquals("recovered", cache.getOrLoad("key").toCompletableFuture().get(5, TimeUnit.SECONDS));
        verify(repository, times(2)).find("key");
    }

    @Test
    void failedSavePropagatesCacheSaveExceptionAndKeepsEntryDirty() throws Exception {
        when(repository.save(anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("disk full")));
        DataCache<String, String> cache = createCache();
        cache.put("key", "value");
        cache.markDirty("key");

        var failure = assertThrows(
                ExecutionException.class,
                () -> cache.save("key").toCompletableFuture().get(5, TimeUnit.SECONDS));

        assertInstanceOf(CacheSaveException.class, findCause(failure, CacheSaveException.class));
        assertEquals(1, cache.dirtyCount());
    }

    @Test
    void concurrentUpdatesDoNotLoseUpdates() throws Exception {
        @SuppressWarnings("unchecked")
        CacheRepository<String, AtomicInteger> integerRepository = mock(CacheRepository.class);
        when(integerRepository.find(anyString())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        DataCache<String, AtomicInteger> cache = CaffeineDataCache.create(
                integerRepository, key -> new AtomicInteger(), scheduler, CacheSettings.staticData());
        cache.put("key", new AtomicInteger());

        int threads = 8;
        int updatesPerThread = 100;
        var latch = new CountDownLatch(threads);
        var executor = Executors.newFixedThreadPool(threads);
        try {
            for (int thread = 0; thread < threads; thread++) {
                executor.execute(() -> {
                    try {
                        for (int update = 0; update < updatesPerThread; update++) {
                            cache.update("key", value -> new AtomicInteger(value.get() + 1))
                                    .toCompletableFuture()
                                    .get(5, TimeUnit.SECONDS);
                        }
                    } catch (Exception failure) {
                        throw new RuntimeException(failure);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(threads * updatesPerThread, cache.get("key").get());
    }

    @Test
    void snapshotIsImmutable() {
        DataCache<String, String> cache = createCache();
        cache.put("a", "1");

        var snapshot = cache.snapshot();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("b", "2"));
        assertEquals(1, snapshot.size());
    }

    @Test
    void operationsAreRejectedAfterClose() {
        when(repository.save(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        DataCache<String, String> cache = createCache();
        cache.put("key", "value");
        cache.close();

        assertThrows(IllegalStateException.class, () -> cache.get("key"));
        assertThrows(IllegalStateException.class, () -> cache.find("key"));
        assertThrows(IllegalStateException.class, () -> cache.getOrLoad("key"));
        assertThrows(IllegalStateException.class, () -> cache.load("key"));
        assertThrows(IllegalStateException.class, () -> cache.update("key", value -> value));
        assertThrows(IllegalStateException.class, () -> cache.mutate("key", value -> {}));
        assertThrows(IllegalStateException.class, () -> cache.put("key", "value"));
        assertThrows(IllegalStateException.class, () -> cache.save("key"));
        assertThrows(IllegalStateException.class, () -> cache.saveDirty());
        assertThrows(IllegalStateException.class, () -> cache.saveAll());
        assertThrows(IllegalStateException.class, () -> cache.unload("key"));
        assertThrows(IllegalStateException.class, () -> cache.markDirty("key"));

        assertFalse(cache.contains("key"));
        assertEquals(0, cache.dirtyCount());
        assertEquals(0, cache.size());
    }

    @Test
    void entriesExpireAccordingToPolicySettings() throws Exception {
        DataCache<String, String> cache = CaffeineDataCache.create(
                repository,
                key -> "default",
                scheduler,
                CacheSettings.builder()
                        .maximumSize(100)
                        .expireAfterWrite(Duration.ofMillis(100))
                        .build());
        cache.put("key", "value");
        assertTrue(cache.contains("key"));

        var expired = new CountDownLatch(1);
        var expiredExecutor = Executors.newSingleThreadScheduledExecutor();
        try {
            var _ = expiredExecutor.schedule(
                    () -> {
                        if (!cache.contains("key")) {
                            expired.countDown();
                        }
                    },
                    500,
                    TimeUnit.MILLISECONDS);

            assertTrue(expired.await(5, TimeUnit.SECONDS));
        } finally {
            expiredExecutor.shutdownNow();
        }
        assertTrue(cache.find("key").isEmpty());
    }

    @Test
    void unloadDuringInFlightLoadDiscardsLoadedValue() throws Exception {
        var gate = new CompletableFuture<Optional<String>>();
        when(repository.find(anyString())).thenReturn(gate);
        DataCache<String, String> cache = createCache();

        var loading = cache.getOrLoad("key").toCompletableFuture();
        cache.unload("key");
        gate.complete(Optional.of("loaded"));

        assertEquals("loaded", loading.get(5, TimeUnit.SECONDS));
        assertFalse(cache.contains("key"));
        assertTrue(cache.find("key").isEmpty());
    }

    @Test
    void unloadOfCleanEntryDoesNotPersist() {
        DataCache<String, String> cache = createCache();
        cache.put("key", "value");

        cache.unload("key");

        verify(repository, never()).save(anyString(), anyString());
    }

    @Test
    void unloadOfDirtyEntrySavesOnCloseWhenSaveOnEvictDisabled() throws Exception {
        when(repository.save(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        DataCache<String, String> cache = createCache();
        cache.put("key", "value");
        cache.markDirty("key");

        cache.unload("key");
        cache.closeAsync().toCompletableFuture().get(5, TimeUnit.SECONDS);

        verify(repository).save("key", "value");
    }

    @Test
    void statsCountHitsAndMisses() {
        DataCache<String, String> cache =
                CaffeineDataCache.create(repository, key -> "default", scheduler, CacheSettings.staticData());

        cache.put("key", "value");
        assertEquals("value", cache.get("key"));
        assertTrue(cache.find("missing").isEmpty());

        var stats = cache.stats();

        assertEquals(1, stats.size());
        assertEquals(1, stats.hitCount());
        assertEquals(2, stats.missCount());
        assertEquals(1, stats.dirtyEntries());
    }

    private static Throwable findCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return current;
            }
        }

        return null;
    }
}
