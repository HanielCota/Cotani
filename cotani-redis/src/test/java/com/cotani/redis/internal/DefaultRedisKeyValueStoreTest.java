package com.cotani.redis.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.redis.codec.RedisCodec;
import com.cotani.redis.store.RedisKey;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultRedisKeyValueStoreTest {

    private StatefulRedisConnection<String, String> stringConnection;
    private StatefulRedisConnection<byte[], byte[]> binaryConnection;
    private RedisAsyncCommands<String, String> stringCommands;
    private RedisAsyncCommands<byte[], byte[]> binaryCommands;
    private DefaultRedisKeyValueStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringConnection = mock(StatefulRedisConnection.class);
        binaryConnection = mock(StatefulRedisConnection.class);
        stringCommands = mock(RedisAsyncCommands.class);
        binaryCommands = mock(RedisAsyncCommands.class);

        when(stringConnection.async()).thenReturn(stringCommands);
        when(binaryConnection.async()).thenReturn(binaryCommands);

        store = new DefaultRedisKeyValueStore(() -> stringConnection, () -> binaryConnection);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetStringValue() {
        var key = RedisKey.of("user:123:name");
        var future = mock(RedisFuture.class);
        when(future.thenApply(any())).thenReturn(CompletableFuture.completedFuture(Optional.of("Alex")));
        when(stringCommands.get(key.value())).thenReturn(future);

        var result = store.getAsync(key).toCompletableFuture().join();

        assertTrue(result.isPresent());
        assertEquals("Alex", result.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSetStringWithTtl() {
        var key = RedisKey.of("user:123:session");
        var future = mock(RedisFuture.class);
        when(future.thenApply(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(stringCommands.set(eq(key.value()), eq("token-abc"), any(SetArgs.class)))
                .thenReturn(future);

        store.setAsync(key, "token-abc", Duration.ofMinutes(30))
                .toCompletableFuture()
                .join();

        verify(stringCommands).set(eq(key.value()), eq("token-abc"), any(SetArgs.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSetTypedObjectWithCodec() {
        var key = RedisKey.of("user:123:data");
        var future = mock(RedisFuture.class);
        when(future.thenApply(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(binaryCommands.set(any(byte[].class), any(byte[].class))).thenReturn(future);

        store.setAsync(key, "CustomData", RedisCodec.string())
                .toCompletableFuture()
                .join();

        verify(binaryCommands)
                .set(
                        eq(key.value().getBytes(StandardCharsets.UTF_8)),
                        eq("CustomData".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIncrementAndDecrement() {
        var key = RedisKey.of("counter:global");
        var incrFuture = mock(RedisFuture.class);
        when(stringCommands.incrby(key.value(), 5L)).thenReturn(incrFuture);
        var _ = store.incrementAndGetAsync(key, 5L);

        verify(stringCommands).incrby(key.value(), 5L);
    }

    @Test
    @SuppressWarnings({"unchecked", "DataFlowIssue", "NullAway"})
    void shouldReturnEmptySetWhenNoKeysFound() {
        var future = mock(RedisFuture.class);
        when(future.thenApply(any())).thenAnswer(inv -> {
            java.util.function.Function<List<String>, Set<RedisKey>> fn = inv.getArgument(0);
            List<String> nullKeys = null;
            return CompletableFuture.completedFuture(fn.apply(nullKeys));
        });
        when(stringCommands.keys("empty:*")).thenReturn(future);

        var keys = store.keysAsync("empty:*").toCompletableFuture().join();
        assertTrue(keys.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldScanKeysMatchingPattern() {
        var keyScanCursor = mock(io.lettuce.core.KeyScanCursor.class);
        when(keyScanCursor.getKeys()).thenReturn(List.of("user:1:profile", "user:2:profile"));
        when(keyScanCursor.isFinished()).thenReturn(true);

        var future = mock(RedisFuture.class);
        when(future.thenCompose(any())).thenAnswer(inv -> {
            java.util.function.Function<
                            io.lettuce.core.KeyScanCursor<String>, java.util.concurrent.CompletionStage<Set<RedisKey>>>
                    fn = inv.getArgument(0);
            return fn.apply(keyScanCursor);
        });

        when(stringCommands.scan(any(io.lettuce.core.ScanCursor.class), any(io.lettuce.core.ScanArgs.class)))
                .thenReturn(future);

        var scanned =
                store.scanKeysAsync("user:*:profile").toCompletableFuture().join();
        assertEquals(2, scanned.size());
        assertTrue(scanned.contains(RedisKey.of("user:1:profile")));
        assertTrue(scanned.contains(RedisKey.of("user:2:profile")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPerformHashOperations() {
        var key = RedisKey.of("player:profile:123");
        var hgetFuture = mock(RedisFuture.class);
        when(hgetFuture.thenApply(any())).thenReturn(CompletableFuture.completedFuture(java.util.Optional.of("VIP")));
        when(stringCommands.hget(key.value(), "rank")).thenReturn(hgetFuture);

        var hsetFuture = mock(RedisFuture.class);
        when(hsetFuture.thenApply(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(stringCommands.hset(key.value(), "rank", "VIP")).thenReturn(hsetFuture);

        var hgetAllFuture = mock(RedisFuture.class);
        when(hgetAllFuture.thenApply(any()))
                .thenReturn(CompletableFuture.completedFuture(java.util.Map.of("rank", "VIP", "coins", "100")));
        when(stringCommands.hgetall(key.value())).thenReturn(hgetAllFuture);

        var hdelFuture = mock(RedisFuture.class);
        when(hdelFuture.thenApply(any())).thenReturn(CompletableFuture.completedFuture(true));
        when(stringCommands.hdel(key.value(), "rank")).thenReturn(hdelFuture);

        var hincrFuture = mock(RedisFuture.class);
        when(hincrFuture.thenApply(any())).thenReturn(CompletableFuture.completedFuture(105L));
        when(stringCommands.hincrby(key.value(), "coins", 5L)).thenReturn(hincrFuture);

        var opt = store.hgetAsync(key, "rank").toCompletableFuture().join();
        assertTrue(opt.isPresent());
        assertEquals("VIP", opt.get());

        store.hsetAsync(key, "rank", "VIP").toCompletableFuture().join();
        verify(stringCommands).hset(key.value(), "rank", "VIP");

        var map = store.hgetAllAsync(key).toCompletableFuture().join();
        assertEquals(2, map.size());
        assertEquals("VIP", map.get("rank"));

        boolean deleted = store.hdelAsync(key, "rank").toCompletableFuture().join();
        assertTrue(deleted);

        long coins = store.hincrByAsync(key, "coins", 5L).toCompletableFuture().join();
        assertEquals(105L, coins);
    }
}
