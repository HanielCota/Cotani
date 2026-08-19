package com.cotani.cache.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class CacheInterfaceDefaultMethodsTest {
    @Test
    void cacheReaderAsyncAliasesDelegateToPrimaryMethods() {
        CacheReader<String, String> reader = reader();
        CompletionStage<String> loaded = CompletableFuture.completedFuture("value");
        when(reader.getOrLoad("key")).thenReturn(loaded);
        when(reader.load("key")).thenReturn(loaded);

        assertSame(loaded, reader.getOrLoadAsync("key"));
        assertSame(loaded, reader.loadAsync("key"));
        verify(reader).getOrLoad("key");
        verify(reader).load("key");
    }

    @Test
    void cacheMutatorAsyncAliasesDelegateToPrimaryMethods() {
        CacheMutator<String, String> mutator = mutator();
        CompletionStage<String> updated = CompletableFuture.completedFuture("new");
        UnaryOperator<String> updater = value -> value + "!";
        Consumer<String> consumer = value -> {};
        when(mutator.update("key", updater)).thenReturn(updated);
        when(mutator.mutate("key", consumer)).thenReturn(updated);

        assertSame(updated, mutator.updateAsync("key", updater));
        assertSame(updated, mutator.mutateAsync("key", consumer));
        verify(mutator).update("key", updater);
        verify(mutator).mutate("key", consumer);
    }

    @Test
    void cachePersistenceAsyncAliasesDelegateToPrimaryMethods() {
        CachePersistence<String> persistence = persistence();
        CompletionStage<Void> done = CompletableFuture.completedFuture(null);
        when(persistence.save("key")).thenReturn(done);
        when(persistence.saveDirty()).thenReturn(done);
        when(persistence.saveAll()).thenReturn(done);

        assertSame(done, persistence.saveAsync("key"));
        assertSame(done, persistence.saveDirtyAsync());
        assertSame(done, persistence.saveAllAsync());
        verify(persistence).save("key");
        verify(persistence).saveDirty();
        verify(persistence).saveAll();
    }

    @Test
    void playerDataCacheAsyncAliasesDelegateToPrimaryMethods() {
        PlayerDataCache<String> cache = playerCache();
        CompletionStage<Void> done = CompletableFuture.completedFuture(null);
        when(cache.saveDirty()).thenReturn(done);
        when(cache.saveAll()).thenReturn(done);

        assertSame(done, cache.saveDirtyAsync());
        assertSame(done, cache.saveAllAsync());
        verify(cache).saveDirty();
        verify(cache).saveAll();
    }

    @Test
    void dataCacheAsyncAliasesDelegateToPrimaryMethods() {
        DataCache<String, String> cache = dataCache();
        CompletionStage<String> value = CompletableFuture.completedFuture("value");
        CompletionStage<Void> done = CompletableFuture.completedFuture(null);
        UnaryOperator<String> updater = current -> current + "!";
        Consumer<String> consumer = current -> {};
        when(cache.getOrLoad("key")).thenReturn(value);
        when(cache.load("key")).thenReturn(value);
        when(cache.update("key", updater)).thenReturn(value);
        when(cache.mutate("key", consumer)).thenReturn(value);
        when(cache.save("key")).thenReturn(done);
        when(cache.saveDirty()).thenReturn(done);
        when(cache.saveAll()).thenReturn(done);

        assertSame(value, cache.getOrLoadAsync("key"));
        assertSame(value, cache.loadAsync("key"));
        assertSame(value, cache.updateAsync("key", updater));
        assertSame(value, cache.mutateAsync("key", consumer));
        assertSame(done, cache.saveAsync("key"));
        assertSame(done, cache.saveDirtyAsync());
        assertSame(done, cache.saveAllAsync());
        verify(cache).getOrLoad("key");
        verify(cache).load("key");
        verify(cache).update("key", updater);
        verify(cache).mutate("key", consumer);
        verify(cache).save("key");
        verify(cache).saveDirty();
        verify(cache).saveAll();
    }

    @SuppressWarnings("unchecked")
    private static CacheReader<String, String> reader() {
        return mock(CacheReader.class, CALLS_REAL_METHODS);
    }

    @SuppressWarnings("unchecked")
    private static CacheMutator<String, String> mutator() {
        return mock(CacheMutator.class, CALLS_REAL_METHODS);
    }

    @SuppressWarnings("unchecked")
    private static CachePersistence<String> persistence() {
        return mock(CachePersistence.class, CALLS_REAL_METHODS);
    }

    @SuppressWarnings("unchecked")
    private static PlayerDataCache<String> playerCache() {
        return mock(PlayerDataCache.class, CALLS_REAL_METHODS);
    }

    @SuppressWarnings("unchecked")
    private static DataCache<String, String> dataCache() {
        return mock(DataCache.class, CALLS_REAL_METHODS);
    }
}
