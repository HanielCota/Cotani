package com.cotani.cache.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cotani.AsyncCloseable;
import com.cotani.cache.api.CacheDiagnostics;
import com.cotani.cache.api.CacheMutator;
import com.cotani.cache.api.CachePersistence;
import com.cotani.cache.api.CacheReader;
import com.cotani.cache.api.DataCache;
import com.cotani.cache.internal.caffeine.CaffeineDataCache;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CacheCapabilityInterfacesTest {
    @Test
    void dataCachePreservesBehaviorThroughNarrowCapabilities() {
        PaperTaskScheduler scheduler = Mockito.mock(PaperTaskScheduler.class);
        when(scheduler.asyncExecutor()).thenReturn(Runnable::run);
        when(scheduler.asyncTimer(any(), any(), any())).thenReturn(SchedulerTask.noop());
        CacheRepository<String, String> repository = repository();
        when(repository.save("key", "value")).thenReturn(CompletableFuture.completedFuture(null));
        DataCache<String, String> cache =
                CaffeineDataCache.create(repository, _ -> "default", scheduler, CacheSettings.temporary());

        CacheReader<String, String> reader = cache;
        CacheMutator<String, String> mutator = cache;
        CachePersistence<String> persistence = cache;
        CacheDiagnostics<String, String> diagnostics = cache;
        AsyncCloseable closeable = cache;

        mutator.put("key", "value");
        assertEquals("value", reader.get("key"));
        assertSame(cache, reader);
        assertEquals(1, diagnostics.size());
        assertTrue(persistence.saveAsync("key").toCompletableFuture().isDone());
        assertTrue(closeable.closeAsync().toCompletableFuture().isDone());
    }

    @SuppressWarnings("unchecked")
    private static CacheRepository<String, String> repository() {
        CacheRepository<String, String> repository = Mockito.mock(CacheRepository.class);
        when(repository.find(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        return repository;
    }
}
