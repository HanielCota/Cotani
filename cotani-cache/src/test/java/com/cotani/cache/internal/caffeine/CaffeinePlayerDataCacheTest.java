package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cache.api.PlayerValueFactory;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("NullAway")
class CaffeinePlayerDataCacheTest {

    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
    private final PlayerValueFactory<String> factory = uuid -> "default-" + uuid;

    @Mock
    private CacheRepository<UUID, String> repository;

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

    private PlayerDataCache<String> createCache() {
        var dataCache =
                new CaffeineDataCache<>(repository, key -> "default-" + key, scheduler, CacheSettings.playerData());
        return new CaffeinePlayerDataCache<>(dataCache, repository, factory, scheduler);
    }

    @Test
    void getThrowsWhenNotLoaded() {
        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        assertThrows(com.cotani.cache.exception.CacheException.class, () -> cache.get(id));
    }

    @Test
    void findReturnsEmptyWhenNotLoaded() {
        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        assertTrue(cache.find(id).isEmpty());
    }

    @Test
    void getOrLoadAsyncLoadsFromRepository() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.of("from-repo")));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        String value = cache.getOrLoadAsync(id).toCompletableFuture().join();

        assertEquals("from-repo", value);
        verify(repository).find(id);
    }

    @Test
    void getOrLoadAsyncUsesFactoryWhenRepositoryReturnsEmpty() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        String value = cache.getOrLoadAsync(id).toCompletableFuture().join();

        assertEquals("default-" + id, value);
    }

    @Test
    void getOrLoadAsyncReturnsExistingWithoutRepositoryCall() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.of("from-repo")));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        cache.getOrLoadAsync(id).toCompletableFuture().join();
        String value = cache.getOrLoadAsync(id).toCompletableFuture().join();

        assertEquals("from-repo", value);
        verify(repository, times(1)).find(id);
    }

    @Test
    void loadAsyncAlwaysReloads() {
        when(repository.find(any(UUID.class)))
                .thenReturn(CompletableFuture.completedFuture(Optional.of("first")))
                .thenReturn(CompletableFuture.completedFuture(Optional.of("second")));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        cache.loadAsync(id).toCompletableFuture().join();
        String reloaded = cache.loadAsync(id).toCompletableFuture().join();

        assertEquals("second", reloaded);
        verify(repository, times(2)).find(id);
    }

    @Test
    void updateAsyncModifiesValue() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.of("old")));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        cache.getOrLoadAsync(id).toCompletableFuture().join();
        String updated =
                cache.updateAsync(id, v -> v + "-new").toCompletableFuture().join();

        assertEquals("old-new", updated);
        assertEquals("old-new", cache.get(id));
    }

    @Test
    void mutateAsyncModifiesValue() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.of("value")));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        cache.getOrLoadAsync(id).toCompletableFuture().join();
        String mutated = cache.mutateAsync(id, v -> {
                    // mutation is in-place for mutable objects; strings are immutable
                })
                .toCompletableFuture()
                .join();

        assertEquals("value", mutated);
    }

    @Test
    void saveAsyncDelegatesToRepository() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.of("value")));
        when(repository.save(any(UUID.class), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        cache.getOrLoadAsync(id).toCompletableFuture().join();
        cache.saveAsync(id).toCompletableFuture().join();

        verify(repository).save(id, "value");
    }

    @Test
    void saveDirtyDelegatesToDataCache() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.of("value")));
        when(repository.save(any(UUID.class), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        cache.getOrLoadAsync(id).toCompletableFuture().join();
        cache.markDirty(id);

        cache.saveDirty().toCompletableFuture().join();

        verify(repository).save(id, "value");
    }

    @Test
    void saveAllDelegatesToDataCache() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.of("value")));
        when(repository.save(any(UUID.class), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        cache.getOrLoadAsync(id).toCompletableFuture().join();
        cache.saveAll().toCompletableFuture().join();

        verify(repository).save(id, "value");
    }

    @Test
    void unloadRemovesEntry() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.of("value")));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        cache.getOrLoadAsync(id).toCompletableFuture().join();
        assertTrue(cache.contains(id));

        cache.unload(id);

        assertFalse(cache.contains(id));
    }

    @Test
    void markDirtySetsDirtyFlag() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.of("value")));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        cache.getOrLoadAsync(id).toCompletableFuture().join();
        assertEquals(0, cache.dirtyCount());

        cache.markDirty(id);

        assertEquals(1, cache.dirtyCount());
    }

    @Test
    void sizeReflectsEntryCount() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.of("value")));

        PlayerDataCache<String> cache = createCache();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        cache.getOrLoadAsync(first).toCompletableFuture().join();
        cache.getOrLoadAsync(second).toCompletableFuture().join();

        assertEquals(2, cache.size());
    }

    @Test
    void closeAsyncDelegatesToDataCache() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.of("value")));
        when(repository.save(any(UUID.class), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        cache.getOrLoadAsync(id).toCompletableFuture().join();
        cache.markDirty(id);

        cache.closeAsync().toCompletableFuture().join();

        verify(repository).save(id, "value");
    }

    @Test
    void closeIsSynchronous() {
        when(repository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.of("value")));
        when(repository.save(any(UUID.class), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        cache.getOrLoadAsync(id).toCompletableFuture().join();
        cache.markDirty(id);

        cache.close();

        assertEquals(0, cache.size());
        verify(repository).save(id, "value");
    }
}
