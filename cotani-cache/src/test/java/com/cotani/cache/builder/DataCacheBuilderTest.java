package com.cotani.cache.builder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.cotani.cache.CotaniCache;
import com.cotani.cache.api.DataCache;
import com.cotani.cache.exception.CacheException;
import com.cotani.cache.policy.CachePreset;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
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
