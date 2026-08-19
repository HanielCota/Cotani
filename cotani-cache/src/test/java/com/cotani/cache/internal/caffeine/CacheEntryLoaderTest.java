package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.cache.entry.CacheEntry;
import com.cotani.cache.exception.CacheLoadException;
import com.cotani.cache.repository.CacheRepository;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("NullAway")
class CacheEntryLoaderTest {
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
    void loadReturnsPersistedValueWhenPresent() {
        when(repository.find("key")).thenReturn(completedStage(Optional.of("persisted")));

        var entry = loader().load("key").join();

        assertEquals("persisted", entry.value());
        verify(repository).find("key");
    }

    @Test
    void loadUsesDefaultValueWhenAbsent() {
        when(repository.find("key")).thenReturn(completedStage(Optional.empty()));

        var entry = loader().load("key").join();

        assertEquals("default:key", entry.value());
    }

    @Test
    void loadAppliesEntryFactoryToResolvedValue() {
        when(repository.find("key")).thenReturn(completedStage(Optional.of("persisted")));

        var entry = loader().load("key").join();

        assertEquals("persisted", entry.value());
        assertFalse(entry.dirty());
    }

    @Test
    void loadWrapsRepositoryFailureInCacheLoadException() {
        when(repository.find("key")).thenReturn(failedStage(new IllegalStateException("db down")));

        var thrown = assertThrows(
                ExecutionException.class, () -> loader().load("key").get());

        assertInstanceOf(CacheLoadException.class, thrown.getCause());
        assertEquals("db down", rootCause(thrown.getCause()).getMessage());
    }

    @Test
    void loadWrapsNullDefaultValueInCacheLoadException() {
        var loader = new CacheEntryLoader<String, String>(repository, key -> null, CacheEntry::of);
        when(repository.find("key")).thenReturn(completedStage(Optional.empty()));

        var thrown =
                assertThrows(ExecutionException.class, () -> loader.load("key").get());

        assertInstanceOf(CacheLoadException.class, thrown.getCause());
        assertInstanceOf(NullPointerException.class, rootCause(thrown.getCause()));
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable root = failure;

        while (root.getCause() != null) {
            root = root.getCause();
        }

        return root;
    }

    @Test
    void loadUsesKeyWhenApplyingDefaultValue() {
        when(repository.find("player")).thenReturn(completedStage(Optional.empty()));

        var entry = loader().load("player").join();

        assertEquals("default:player", entry.value());
    }

    private static <T> CompletableFuture<T> completedStage(T value) {
        return CompletableFuture.completedFuture(value);
    }

    private static <T> CompletableFuture<T> failedStage(Throwable failure) {
        var future = new CompletableFuture<T>();
        future.completeExceptionally(failure);
        return future;
    }
}
