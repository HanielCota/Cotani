package com.cotani.redis.leaderboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.redis.internal.DefaultRedisSortedSetStore;
import com.cotani.redis.store.RedisKey;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisSortedSetStoreTest {

    private StatefulRedisConnection<String, String> connection;
    private RedisAsyncCommands<String, String> commands;
    private DefaultRedisSortedSetStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        connection = mock(StatefulRedisConnection.class);
        commands = mock(RedisAsyncCommands.class);
        when(connection.async()).thenReturn(commands);
        store = new DefaultRedisSortedSetStore(() -> connection);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAddScoreAndIncrement() {
        var key = RedisKey.of("leaderboard:kills");
        var addFuture = mock(RedisFuture.class);
        when(addFuture.thenApply(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(commands.zadd(key.value(), 10.0, "Player1")).thenReturn(addFuture);

        var incrFuture = mock(RedisFuture.class);
        when(incrFuture.thenApply(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(15.0));
        when(commands.zincrby(key.value(), 5.0, "Player1")).thenReturn(incrFuture);

        boolean added = store.addOrUpdateScoreAsync(key, "Player1", 10.0)
                .toCompletableFuture()
                .join();
        assertTrue(added);

        double newScore = store.incrementScoreAsync(key, "Player1", 5.0)
                .toCompletableFuture()
                .join();
        assertEquals(15.0, newScore);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetRankAndScore() {
        var key = RedisKey.of("leaderboard:kills");
        var rankFuture = mock(RedisFuture.class);
        when(rankFuture.thenApply(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(java.util.Optional.of(1L)));
        when(commands.zrevrank(key.value(), "Player1")).thenReturn(rankFuture);

        var scoreFuture = mock(RedisFuture.class);
        when(scoreFuture.thenApply(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(java.util.Optional.of(15.0)));
        when(commands.zscore(key.value(), "Player1")).thenReturn(scoreFuture);

        var rank = store.getRankAsync(key, "Player1").toCompletableFuture().join();
        var score = store.getScoreAsync(key, "Player1").toCompletableFuture().join();

        assertTrue(rank.isPresent());
        assertEquals(1L, rank.get());
        assertTrue(score.isPresent());
        assertEquals(15.0, score.get());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldGetTopLeaderboardEntries() {
        var key = RedisKey.of("leaderboard:kills");
        var rangeFuture = mock(RedisFuture.class);
        List<ScoredValue<String>> scoredValues =
                List.of(ScoredValue.just(100.0, "PlayerTop"), ScoredValue.just(80.0, "PlayerSecond"));

        when(rangeFuture.thenApply(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            java.util.function.Function<List<ScoredValue<String>>, List<RankEntry>> fn = inv.getArgument(0);
            return CompletableFuture.completedFuture(fn.apply(scoredValues));
        });
        when(commands.zrevrangeWithScores(key.value(), 0, 9)).thenReturn(rangeFuture);

        List<RankEntry> top = store.getTopAsync(key, 10).toCompletableFuture().join();
        assertEquals(2, top.size());
        assertEquals("PlayerTop", top.get(0).member());
        assertEquals(100.0, top.get(0).score());
        assertEquals(1L, top.get(0).rank());

        assertEquals("PlayerSecond", top.get(1).member());
        assertEquals(80.0, top.get(1).score());
        assertEquals(2L, top.get(1).rank());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRemoveAndGetSize() {
        var key = RedisKey.of("leaderboard:kills");
        var remFuture = mock(RedisFuture.class);
        when(remFuture.thenApply(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(commands.zrem(key.value(), "Player1")).thenReturn(remFuture);

        var cardFuture = mock(RedisFuture.class);
        when(cardFuture.thenApply(org.mockito.ArgumentMatchers.any()))
                .thenReturn(CompletableFuture.completedFuture(5L));
        when(commands.zcard(key.value())).thenReturn(cardFuture);

        boolean removed =
                store.removeAsync(key, "Player1").toCompletableFuture().join();
        long size = store.sizeAsync(key).toCompletableFuture().join();

        assertTrue(removed);
        assertEquals(5L, size);
    }
}
