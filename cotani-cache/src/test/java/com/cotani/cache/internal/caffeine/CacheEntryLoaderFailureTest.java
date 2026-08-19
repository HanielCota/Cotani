package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.cache.entry.CacheEntry;
import com.cotani.cache.exception.CacheLoadException;
import com.cotani.cache.repository.CacheRepository;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("NullAway")
class CacheEntryLoaderFailureTest {
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

    private CacheEntryLoader<String, String> loader() {
        return new CacheEntryLoader<>(repository, key -> "default:" + key, CacheEntry::of);
    }

    @Test
    void repositoryReturningNullStageIsRejectedSynchronously() {
        var loader = new CacheEntryLoader<>(nullRepository(), key -> "default", CacheEntry::of);

        assertThrows(NullPointerException.class, () -> loader.load("key"));
    }

    @Test
    void failedLoadDoesNotPoisonSubsequentLoads() throws Exception {
        when(repository.find("key"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db down")))
                .thenReturn(CompletableFuture.completedFuture(Optional.of("recovered")));
        var loader = loader();

        var firstFailure =
                assertThrows(ExecutionException.class, () -> loader.load("key").get(5, TimeUnit.SECONDS));

        assertInstanceOf(CacheLoadException.class, firstFailure.getCause());

        assertEquals("recovered", loader.load("key").get(5, TimeUnit.SECONDS).value());
        verify(repository, times(2)).find("key");
    }

    private static CacheRepository<String, String> nullRepository() {
        return new CacheRepository<String, String>() {
            @Override
            @SuppressWarnings("NullAway")
            public CompletionStage<Optional<String>> find(String key) {
                return null;
            }

            @Override
            public CompletionStage<Void> save(String key, String value) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> delete(String key) {
                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
