package com.cotani.cache.builder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.cache.CotaniCache;
import com.cotani.cache.api.DataCache;
import com.cotani.cache.policy.CachePreset;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class DataCacheBuilderBehaviorTest {
    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);

    @BeforeEach
    void setUp() {
        when(scheduler.asyncExecutor()).thenReturn(CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS));
        when(scheduler.asyncTimer(any(), any(), any())).thenReturn(SchedulerTask.noop());
    }

    @Test
    void settingsNullRejects() {
        var builder = CotaniCache.data(String.class, String.class).defaultValue(() -> "default");

        assertThrows(NullPointerException.class, () -> builder.settings(null));
    }

    @Test
    void presetThenOverrideBuildsSuccessfully() {
        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .preset(CachePreset.TEMPORARY)
                .maximumSize(500)
                .expireAfterAccess(Duration.ofMinutes(5))
                .build(scheduler);

        assertNotNull(cache);
        assertEquals(0, cache.size());
    }

    @Test
    void buildWithNegativeMaximumSizeFailsFast() {
        var builder = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .maximumSize(-1);

        assertThrows(IllegalArgumentException.class, () -> builder.build(scheduler));
    }

    @Test
    void buildWithZeroMaximumSizeSucceeds() {
        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .maximumSize(0)
                .build(scheduler);

        assertNotNull(cache);
    }

    @Test
    void maximumConcurrentSavesLimitsSaveAllConcurrency() {
        var controlledRepository = new ControlledRepository();
        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .repository(controlledRepository)
                .maximumConcurrentSaves(2)
                .build(scheduler);
        for (int index = 0; index < 100; index++) {
            cache.put("key-" + index, "value-" + index);
        }

        var save = cache.saveAll().toCompletableFuture();
        assertEquals(2, controlledRepository.active.get());
        assertEquals(2, controlledRepository.peak.get());

        for (int completed = 0; completed < 100; completed++) {
            var pending = controlledRepository.pending.poll();

            if (pending == null) {
                throw new AssertionError("bulk coordinator stopped before all saves were admitted");
            }

            pending.complete(null);
        }

        save.join();
        assertEquals(0, controlledRepository.active.get());
        assertEquals(2, controlledRepository.peak.get());
        cache.closeAsync().toCompletableFuture().join();
    }

    private static final class ControlledRepository implements CacheRepository<String, String> {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();
        private final ConcurrentLinkedQueue<CompletableFuture<Void>> pending = new ConcurrentLinkedQueue<>();
        private final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>();

        @Override
        public CompletionStage<Optional<String>> find(String key) {
            return CompletableFuture.completedFuture(Optional.ofNullable(values.get(key)));
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
