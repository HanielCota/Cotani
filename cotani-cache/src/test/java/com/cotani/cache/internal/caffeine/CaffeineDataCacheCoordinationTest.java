package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.cache.invalidation.LocalCacheInvalidationBus;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CaffeineDataCacheCoordinationTest {

    @Test
    void sharedBusInvalidatesCleanEntriesAcrossCacheInstances() {
        var repository = new MapRepository();
        var bus = new LocalCacheInvalidationBus<String>();
        var first = new CaffeineDataCache<>(repository, _ -> "missing", scheduler(), CacheSettings.temporary(), 4, bus);
        var second =
                new CaffeineDataCache<>(repository, _ -> "missing", scheduler(), CacheSettings.temporary(), 4, bus);

        repository.values.put("key", "old");
        assertEquals("old", first.getOrLoad("key").toCompletableFuture().join());
        assertEquals("old", second.getOrLoad("key").toCompletableFuture().join());

        first.put("key", "new");
        first.save("key").toCompletableFuture().join();

        assertFalse(second.contains("key"));
        assertEquals("new", second.getOrLoad("key").toCompletableFuture().join());
        first.closeAsync().toCompletableFuture().join();
        second.closeAsync().toCompletableFuture().join();
    }

    @Test
    void remoteInvalidationNeverDropsDirtyLocalState() {
        var repository = new MapRepository();
        var bus = new LocalCacheInvalidationBus<String>();
        var first = new CaffeineDataCache<>(repository, _ -> "missing", scheduler(), CacheSettings.temporary(), 4, bus);
        var second =
                new CaffeineDataCache<>(repository, _ -> "missing", scheduler(), CacheSettings.temporary(), 4, bus);
        repository.values.put("key", "old");
        first.getOrLoad("key").toCompletableFuture().join();
        second.getOrLoad("key").toCompletableFuture().join();
        second.update("key", _ -> "local-dirty").toCompletableFuture().join();

        first.put("key", "remote");
        first.save("key").toCompletableFuture().join();

        assertTrue(second.contains("key"));
        assertEquals("local-dirty", second.get("key"));
        first.closeAsync().toCompletableFuture().join();
        second.closeAsync().toCompletableFuture().join();
    }

    @Test
    void saveAllKeepsRepositoryConcurrencyWithinConfiguredLimit() {
        var repository = new ControlledRepository();
        var cache = new CaffeineDataCache<>(
                repository,
                _ -> "missing",
                scheduler(),
                CacheSettings.temporary(),
                4,
                new LocalCacheInvalidationBus<>());
        for (int i = 0; i < 10_000; i++) {
            cache.put("key-" + i, "value-" + i);
        }

        var save = cache.saveAll().toCompletableFuture();
        assertEquals(4, repository.active.get());
        assertEquals(4, repository.peak.get());

        for (int completed = 0; completed < 10_000; completed++) {
            var pending = repository.pending.poll();
            if (pending == null) {
                throw new AssertionError("bulk coordinator stopped before all saves were admitted");
            }
            pending.complete(null);
        }

        save.join();
        assertEquals(0, repository.active.get());
        assertEquals(4, repository.peak.get());
        cache.closeAsync().toCompletableFuture().join();
    }

    private static PaperTaskScheduler scheduler() {
        PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
        when(scheduler.asyncExecutor()).thenReturn(Runnable::run);
        when(scheduler.asyncTimer(any(), any(), any())).thenReturn(SchedulerTask.noop());
        return scheduler;
    }

    private static final class MapRepository implements CacheRepository<String, String> {
        private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();

        @Override
        public CompletionStage<Optional<String>> find(String key) {
            return CompletableFuture.completedFuture(Optional.ofNullable(values.get(key)));
        }

        @Override
        public CompletionStage<Void> save(String key, String value) {
            values.put(key, value);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> delete(String key) {
            values.remove(key);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ControlledRepository implements CacheRepository<String, String> {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();
        private final ConcurrentLinkedQueue<CompletableFuture<Void>> pending = new ConcurrentLinkedQueue<>();

        @Override
        public CompletionStage<Optional<String>> find(String key) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletionStage<Void> save(String key, String value) {
            int current = active.incrementAndGet();
            peak.accumulateAndGet(current, Math::max);
            var gate = new CompletableFuture<Void>();
            pending.add(gate);
            return gate.whenComplete((_, _) -> active.decrementAndGet());
        }

        @Override
        public CompletionStage<Void> delete(String key) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
