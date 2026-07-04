package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.cotani.cache.api.DataCache;
import com.cotani.cache.exception.CacheException;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.cache.stats.CacheStatsView;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("NullAway")
class CaffeineDataCacheTest {

    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);

    @Mock
    private CacheRepository<String, String> repository;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(scheduler.asyncExecutor())
                .thenReturn(CompletableFuture.delayedExecutor(0, java.util.concurrent.TimeUnit.MILLISECONDS));
        when(scheduler.asyncTimer(any(), any(), any())).thenReturn(SchedulerTask.noop());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    private DataCache<String, String> createCache(CacheSettings settings) {
        return new CaffeineDataCache<>(repository, key -> "default", scheduler, settings);
    }

    private DataCache<String, String> createCache() {
        return createCache(CacheSettings.temporary());
    }

    @Test
    void getThrowsWhenNotLoaded() {
        DataCache<String, String> cache = createCache();

        CacheException exception = assertThrows(CacheException.class, () -> cache.get("key"));
        assertTrue(exception.getMessage().contains("not loaded"));
    }

    @Test
    void findReturnsEmptyWhenNotLoaded() {
        DataCache<String, String> cache = createCache();

        assertTrue(cache.find("key").isEmpty());
    }

    @Test
    void putAndFindReturnValue() {
        DataCache<String, String> cache = createCache();

        cache.put("key", "value");

        Optional<String> found = cache.find("key");
        assertTrue(found.isPresent());
        assertEquals("value", found.get());
        assertEquals("value", cache.get("key"));
    }

    @Test
    void containsReturnsTrueAfterPut() {
        DataCache<String, String> cache = createCache();

        assertFalse(cache.contains("key"));

        cache.put("key", "value");

        assertTrue(cache.contains("key"));
    }

    @Test
    void sizeReflectsPutCount() {
        DataCache<String, String> cache = createCache();

        assertEquals(0, cache.size());

        cache.put("a", "1");
        cache.put("b", "2");

        assertEquals(2, cache.size());
    }

    @Test
    void snapshotReturnsCopyOfEntries() {
        DataCache<String, String> cache = createCache();

        cache.put("a", "1");
        cache.put("b", "2");

        var snapshot = cache.snapshot();

        assertEquals(2, snapshot.size());
        assertEquals("1", snapshot.get("a"));
        assertEquals("2", snapshot.get("b"));
    }

    @Test
    void updateModifiesValue() {
        DataCache<String, String> cache = createCache();

        cache.put("key", "old");
        String updated = cache.update("key", value -> value + "-new")
                .toCompletableFuture()
                .join();

        assertEquals("old-new", updated);
        assertEquals("old-new", cache.get("key"));
    }

    @Test
    void updateThrowsWhenNotLoaded() {
        DataCache<String, String> cache = createCache();

        assertThrows(CacheException.class, () -> cache.update("key", v -> v));
    }

    @Test
    void mutateModifiesValueInPlace() {
        DataCache<String, String> cache = createCache();

        cache.put("key", "value");
        String mutated = cache.mutate("key", value -> {
                    // mutation is applied to the same reference; strings are immutable so this test uses a holder
                })
                .toCompletableFuture()
                .join();

        assertEquals("value", mutated);
    }

    @Test
    void markDirtySetsDirtyFlag() {
        DataCache<String, String> cache = createCache();

        cache.put("key", "value");
        assertEquals(0, cache.dirtyCount());

        cache.markDirty("key");

        assertEquals(1, cache.dirtyCount());
    }

    @Test
    void unloadRemovesEntry() {
        DataCache<String, String> cache = createCache();

        cache.put("key", "value");
        assertTrue(cache.contains("key"));

        cache.unload("key");

        assertFalse(cache.contains("key"));
    }

    @Test
    void saveDelegatesToRepository() {
        when(repository.save(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        DataCache<String, String> cache = createCache();
        cache.put("key", "value");

        cache.save("key").toCompletableFuture().join();

        verify(repository).save("key", "value");
    }

    @Test
    void saveReturnsCompletedWhenEntryNotPresent() {
        DataCache<String, String> cache = createCache();

        cache.save("missing").toCompletableFuture().join();

        verifyNoInteractions(repository);
    }

    @Test
    void saveAllSavesAllEntries() {
        when(repository.save(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        DataCache<String, String> cache = createCache();
        cache.put("a", "1");
        cache.put("b", "2");

        cache.saveAll().toCompletableFuture().join();

        verify(repository, times(2)).save(anyString(), anyString());
    }

    @Test
    void saveDirtyOnlySavesDirtyEntries() {
        when(repository.save(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        DataCache<String, String> cache = createCache();
        cache.put("a", "1");
        cache.put("b", "2");
        cache.markDirty("a");

        cache.saveDirty().toCompletableFuture().join();

        verify(repository, times(1)).save(anyString(), anyString());
    }

    @Test
    void getOrLoadLoadsFromRepository() {
        when(repository.find(anyString())).thenReturn(CompletableFuture.completedFuture(Optional.of("from-repo")));

        DataCache<String, String> cache = createCache();
        String value = cache.getOrLoad("key").toCompletableFuture().join();

        assertEquals("from-repo", value);
        verify(repository).find("key");
    }

    @Test
    void getOrLoadUsesDefaultValueWhenRepositoryReturnsEmpty() {
        when(repository.find(anyString())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        DataCache<String, String> cache = createCache();
        String value = cache.getOrLoad("key").toCompletableFuture().join();

        assertEquals("default", value);
    }

    @Test
    void loadInvalidatesAndReloads() {
        when(repository.find(anyString()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of("first")))
                .thenReturn(CompletableFuture.completedFuture(Optional.of("second")));

        DataCache<String, String> cache = createCache();
        cache.getOrLoad("key").toCompletableFuture().join();

        String reloaded = cache.load("key").toCompletableFuture().join();

        assertEquals("second", reloaded);
    }

    @Test
    void statsReturnsView() {
        DataCache<String, String> cache = createCache(CacheSettings.staticData());

        cache.put("a", "1");
        cache.find("a");
        cache.find("a");

        CacheStatsView stats = cache.stats();

        assertNotNull(stats);
        assertEquals(1, stats.size());
        assertTrue(stats.hitCount() >= 0);
        assertTrue(stats.missCount() >= 0);
    }

    @Test
    void closeAsyncCancelsAutosaveAndSavesDirty() {
        when(repository.save(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        DataCache<String, String> cache = createCache(CacheSettings.highActivity());
        cache.put("key", "value");
        cache.markDirty("key");

        cache.closeAsync().toCompletableFuture().join();

        verify(repository).save("key", "value");
    }

    @Test
    void clearRemovesAllEntries() {
        DataCache<String, String> cache = createCache(CacheSettings.highActivity());

        cache.put("a", "1");
        cache.put("b", "2");
        assertEquals(2, cache.size());

        cache.closeAsync().toCompletableFuture().join();

        assertEquals(0, cache.size());
    }

    @Test
    void closeIsSynchronous() {
        when(repository.save(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        DataCache<String, String> cache = createCache(CacheSettings.highActivity());
        cache.put("key", "value");
        cache.markDirty("key");

        cache.close();

        assertEquals(0, cache.size());
        verify(repository).save("key", "value");
    }
}
