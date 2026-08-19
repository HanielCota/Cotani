package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("NullAway")
class CaffeinePlayerDataCacheBehaviorTest {
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
                CaffeineDataCache.create(repository, key -> "default-" + key, scheduler, CacheSettings.playerData());

        return CaffeinePlayerDataCache.create(dataCache, repository, factory, scheduler);
    }

    @Test
    void concurrentGetOrLoadAsyncForSamePlayerTriggersSingleRepositoryLoad() throws Exception {
        var gate = new CompletableFuture<Optional<String>>();
        var loadStarted = new CountDownLatch(1);
        when(repository.find(any(UUID.class))).thenAnswer(_ -> {
            loadStarted.countDown();
            return gate;
        });
        PlayerDataCache<String> cache = createCache();
        UUID id = UUID.randomUUID();

        var first = cache.getOrLoadAsync(id).toCompletableFuture();
        var second = cache.getOrLoadAsync(id).toCompletableFuture();

        assertTrue(loadStarted.await(5, TimeUnit.SECONDS));
        verify(repository, times(1)).find(id);

        gate.complete(Optional.of("shared"));
        assertEquals("shared", first.get(5, TimeUnit.SECONDS));
        assertEquals("shared", second.get(5, TimeUnit.SECONDS));
        verify(repository, times(1)).find(id);
    }

    @Test
    void operationsAreRejectedAfterClose() {
        PlayerDataCache<String> cache = createCache();
        cache.close();

        assertThrows(IllegalStateException.class, () -> cache.get(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> cache.find(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> cache.getOrLoadAsync(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> cache.loadAsync(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> cache.updateAsync(UUID.randomUUID(), value -> value));
        assertThrows(IllegalStateException.class, () -> cache.mutateAsync(UUID.randomUUID(), value -> {}));
        assertThrows(IllegalStateException.class, () -> cache.saveAsync(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> cache.saveDirty());
        assertThrows(IllegalStateException.class, () -> cache.saveAll());
        assertThrows(IllegalStateException.class, () -> cache.unload(UUID.randomUUID()));
        assertThrows(IllegalStateException.class, () -> cache.markDirty(UUID.randomUUID()));

        assertFalse(cache.contains(UUID.randomUUID()));
        assertEquals(0, cache.dirtyCount());
        assertEquals(0, cache.size());
    }

    @Test
    void nullUniqueIdRejects() {
        PlayerDataCache<String> cache = createCache();

        assertThrows(NullPointerException.class, () -> cache.get((UUID) null));
        assertThrows(NullPointerException.class, () -> cache.find((UUID) null));
        assertThrows(NullPointerException.class, () -> cache.getOrLoadAsync(null));
        assertThrows(NullPointerException.class, () -> cache.loadAsync(null));
        assertThrows(NullPointerException.class, () -> cache.updateAsync(null, value -> value));
        assertThrows(NullPointerException.class, () -> cache.saveAsync(null));
        assertThrows(NullPointerException.class, () -> cache.unload((UUID) null));
        assertThrows(NullPointerException.class, () -> cache.contains((UUID) null));
        assertThrows(NullPointerException.class, () -> cache.markDirty((UUID) null));
    }
}
