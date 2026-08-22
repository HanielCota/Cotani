package com.cotani.redis.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.redis.codec.RedisCodec;
import com.cotani.redis.store.RedisKey;
import com.cotani.redis.store.RedisKeyValueStore;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisCacheRepositoryTest {

    private RedisKeyValueStore store;
    private RedisCacheRepository<String, String> cacheRepo;

    @BeforeEach
    void setUp() {
        store = mock(RedisKeyValueStore.class);
        cacheRepo = RedisCacheRepository.ofPrefix(store, "player:cache", RedisCodec.string());
    }

    @Test
    void shouldFindSaveAndDelete() {
        var key = "user-123";
        var redisKey = RedisKey.of("player:cache:user-123");

        when(store.getAsync(redisKey, RedisCodec.string()))
                .thenReturn(CompletableFuture.completedFuture(Optional.of("cached_value")));
        when(store.setAsync(redisKey, "new_value", RedisCodec.string()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(store.deleteAsync(redisKey)).thenReturn(CompletableFuture.completedFuture(true));

        var opt = cacheRepo.find(key).toCompletableFuture().join();
        assertTrue(opt.isPresent());
        assertEquals("cached_value", opt.get());

        cacheRepo.save(key, "new_value").toCompletableFuture().join();
        verify(store).setAsync(redisKey, "new_value", RedisCodec.string());

        cacheRepo.delete(key).toCompletableFuture().join();
        verify(store).deleteAsync(redisKey);
    }
}
