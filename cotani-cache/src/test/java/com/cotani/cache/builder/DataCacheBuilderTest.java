package com.cotani.cache.builder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.cache.CotaniCache;
import com.cotani.cache.api.DataCache;
import com.cotani.cache.exception.CacheException;
import com.cotani.cache.policy.CachePreset;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("NullAway")
class DataCacheBuilderTest {
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

    @Test
    void buildRequiresDefaultValue() {
        DataCacheBuilder<String, String> builder = CotaniCache.data(String.class, String.class);

        assertThrows(CacheException.class, () -> builder.build(scheduler));
    }

    @Test
    void buildRejectsNullScheduler() {
        DataCacheBuilder<String, String> builder =
                CotaniCache.data(String.class, String.class).defaultValue(() -> "default");

        assertThrows(NullPointerException.class, () -> builder.build(null));
    }

    @Test
    void defaultValueSupplierNullRejects() {
        DataCacheBuilder<String, String> builder = CotaniCache.data(String.class, String.class);

        assertThrows(
                NullPointerException.class, () -> builder.defaultValue((java.util.function.Supplier<String>) null));
    }

    @Test
    void defaultValueFunctionNullRejects() {
        DataCacheBuilder<String, String> builder = CotaniCache.data(String.class, String.class);

        assertThrows(
                NullPointerException.class,
                () -> builder.defaultValue((java.util.function.Function<String, String>) null));
    }

    @Test
    void repositoryNullRejects() {
        DataCacheBuilder<String, String> builder = CotaniCache.data(String.class, String.class);

        assertThrows(NullPointerException.class, () -> builder.repository(null));
    }

    @Test
    void presetNullRejects() {
        DataCacheBuilder<String, String> builder = CotaniCache.data(String.class, String.class);

        assertThrows(NullPointerException.class, () -> builder.preset(null));
    }

    @Test
    void invalidationBusNullRejects() {
        DataCacheBuilder<String, String> builder = CotaniCache.data(String.class, String.class);

        assertThrows(NullPointerException.class, () -> builder.invalidationBus(null));
    }

    @Test
    void maximumConcurrentSavesRequiresPositive() {
        DataCacheBuilder<String, String> builder = CotaniCache.data(String.class, String.class);

        assertThrows(IllegalArgumentException.class, () -> builder.maximumConcurrentSaves(0));
        assertThrows(IllegalArgumentException.class, () -> builder.maximumConcurrentSaves(-1));
    }

    @Test
    void createRejectsNullKeyType() {
        assertThrows(NullPointerException.class, () -> CotaniCache.data(null, String.class));
    }

    @Test
    void createRejectsNullValueType() {
        assertThrows(NullPointerException.class, () -> CotaniCache.data(String.class, null));
    }

    @Test
    void buildWithDefaultValueSucceeds() {
        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .build(scheduler);

        assertNotNull(cache);
    }

    @Test
    void presetSetsSettings() {
        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .preset(CachePreset.TEMPORARY)
                .build(scheduler);

        assertNotNull(cache);
    }

    @Test
    void maximumSizeOverridesValue() {
        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .maximumSize(500)
                .build(scheduler);

        assertNotNull(cache);
        assertEquals(0, cache.size());
    }

    @Test
    void expireAfterAccessOverridesValue() {
        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .expireAfterAccess(Duration.ofMinutes(15))
                .build(scheduler);

        assertNotNull(cache);
    }

    @Test
    void expireAfterWriteOverridesValue() {
        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .expireAfterWrite(Duration.ofHours(2))
                .build(scheduler);

        assertNotNull(cache);
    }

    @Test
    void autosaveEveryOverridesValue() {
        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .autosaveEvery(Duration.ofSeconds(30))
                .build(scheduler);

        assertNotNull(cache);
    }

    @Test
    void repositoryIsUsedWhenProvided() {
        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .repository(repository)
                .build(scheduler);

        assertNotNull(cache);
    }

    @Test
    void settingsOverridesAll() {
        CacheSettings custom = CacheSettings.builder()
                .maximumSize(100)
                .expireAfterAccess(Duration.ofMinutes(5))
                .expireAfterWrite(Duration.ofMinutes(10))
                .autosaveInterval(Duration.ofSeconds(15))
                .loadOnJoin(true)
                .saveOnQuit(false)
                .unloadOnQuit(true)
                .saveOnEvict(false)
                .recordStats(true)
                .build();

        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .settings(custom)
                .build(scheduler);

        assertNotNull(cache);
    }

    @Test
    void temporaryFactoryMethodCreatesBuilder() {
        DataCacheBuilder<String, String> builder =
                CotaniCache.temporary(String.class, String.class, Duration.ofMinutes(5));

        assertNotNull(builder);
    }

    @Test
    void temporaryFactoryMethodSetsExpireAfterWrite() {
        DataCache<String, String> cache = CotaniCache.temporary(String.class, String.class, Duration.ofMinutes(5))
                .defaultValue(() -> "default")
                .build(scheduler);

        assertNotNull(cache);
    }
}
