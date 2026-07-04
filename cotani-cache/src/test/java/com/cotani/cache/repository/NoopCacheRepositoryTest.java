package com.cotani.cache.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class NoopCacheRepositoryTest {

    @Test
    void findReturnsEmptyOptional() {
        NoopCacheRepository<String, String> repo = new NoopCacheRepository<>();

        var result = repo.find("key").toCompletableFuture().join();

        assertTrue(result.isEmpty());
    }

    @Test
    void saveReturnsNull() {
        NoopCacheRepository<String, String> repo = new NoopCacheRepository<>();

        repo.save("key", "value").toCompletableFuture().join();
    }

    @Test
    void deleteReturnsNull() {
        NoopCacheRepository<String, String> repo = new NoopCacheRepository<>();

        repo.delete("key").toCompletableFuture().join();
    }
}
