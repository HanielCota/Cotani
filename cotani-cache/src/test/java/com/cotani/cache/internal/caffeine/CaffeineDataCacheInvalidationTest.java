package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.cache.api.DataCache;
import com.cotani.cache.invalidation.CacheInvalidation;
import com.cotani.cache.invalidation.LocalCacheInvalidationBus;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("NullAway")
class CaffeineDataCacheInvalidationTest {
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

    private CaffeineDataCache<String, String> createCache(LocalCacheInvalidationBus<String> bus) {
        return CaffeineDataCache.create(repository, key -> "default", scheduler, CacheSettings.temporary(), 16, bus);
    }

    private static UUID cacheIdOf(CaffeineDataCache<String, String> cache) {
        try {
            var field = CaffeineDataCache.class.getDeclaredField("cacheId");
            field.setAccessible(true);

            return (UUID) field.get(cache);
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException("Failed to read cacheId", failure);
        }
    }

    @Test
    void invalidationFromOtherSourceEvictsCleanEntry() {
        var bus = new LocalCacheInvalidationBus<String>();
        DataCache<String, String> cache = createCache(bus);
        cache.put("key", "value");

        bus.publish(new CacheInvalidation<>(UUID.randomUUID(), "key"))
                .toCompletableFuture()
                .join();

        assertFalse(cache.contains("key"));
    }

    @Test
    void invalidationFromOwnSourceIsIgnored() {
        var bus = new LocalCacheInvalidationBus<String>();
        CaffeineDataCache<String, String> cache = createCache(bus);
        cache.put("key", "value");

        bus.publish(new CacheInvalidation<>(cacheIdOf(cache), "key"))
                .toCompletableFuture()
                .join();

        assertTrue(cache.contains("key"));
    }

    @Test
    void invalidationDoesNotEvictDirtyEntry() {
        var bus = new LocalCacheInvalidationBus<String>();
        DataCache<String, String> cache = createCache(bus);
        cache.put("key", "value");
        cache.markDirty("key");

        bus.publish(new CacheInvalidation<>(UUID.randomUUID(), "key"))
                .toCompletableFuture()
                .join();

        assertTrue(cache.contains("key"));
    }

    @Test
    void invalidationForMissingEntryIsNoop() {
        var bus = new LocalCacheInvalidationBus<String>();
        DataCache<String, String> cache = createCache(bus);

        bus.publish(new CacheInvalidation<>(UUID.randomUUID(), "missing"))
                .toCompletableFuture()
                .join();

        assertFalse(cache.contains("missing"));
    }

    @Test
    void invalidationAfterCloseIsIgnored() {
        var bus = new LocalCacheInvalidationBus<String>();
        DataCache<String, String> cache = createCache(bus);
        cache.put("key", "value");
        cache.close();

        bus.publish(new CacheInvalidation<>(UUID.randomUUID(), "key"))
                .toCompletableFuture()
                .join();

        assertThrows(IllegalStateException.class, () -> cache.put("other", "value"));
    }
}
