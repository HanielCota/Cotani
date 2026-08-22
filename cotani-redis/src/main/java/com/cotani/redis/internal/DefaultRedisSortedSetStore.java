package com.cotani.redis.internal;

import com.cotani.api.InternalApi;
import com.cotani.redis.leaderboard.RankEntry;
import com.cotani.redis.leaderboard.RedisSortedSetStore;
import com.cotani.redis.store.RedisKey;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

@InternalApi
public final class DefaultRedisSortedSetStore implements RedisSortedSetStore {

    private final Supplier<StatefulRedisConnection<String, String>> stringConnectionSupplier;

    public DefaultRedisSortedSetStore(Supplier<StatefulRedisConnection<String, String>> stringConnectionSupplier) {
        this.stringConnectionSupplier = Objects.requireNonNull(stringConnectionSupplier, "stringConnectionSupplier");
    }

    private RedisAsyncCommands<String, String> requireCommands() {
        var connection = stringConnectionSupplier.get();
        if (connection == null) {
            throw new IllegalStateException("Redis connection is not active");
        }
        return connection.async();
    }

    @Override
    public CompletionStage<Boolean> addOrUpdateScoreAsync(RedisKey key, String member, double score) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(member, "member");
        return requireCommands().zadd(key.value(), score, member).thenApply(count -> count != null && count > 0);
    }

    @Override
    public CompletionStage<Double> incrementScoreAsync(RedisKey key, String member, double delta) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(member, "member");
        return requireCommands().zincrby(key.value(), delta, member).thenApply(newScore -> {
            if (newScore == null) {
                return delta;
            }
            return newScore;
        });
    }

    @Override
    public CompletionStage<Optional<Double>> getScoreAsync(RedisKey key, String member) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(member, "member");
        return requireCommands().zscore(key.value(), member).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletionStage<Optional<Long>> getRankAsync(RedisKey key, String member) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(member, "member");
        return requireCommands().zrevrank(key.value(), member).thenApply(rank -> {
            if (rank == null) {
                return Optional.empty();
            }
            return Optional.of(rank + 1);
        });
    }

    @Override
    public CompletionStage<List<RankEntry>> getTopAsync(RedisKey key, int limit) {
        Objects.requireNonNull(key, "key");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        return getRangeAsync(key, 0, limit - 1L);
    }

    @Override
    public CompletionStage<List<RankEntry>> getRangeAsync(RedisKey key, long start, long stop) {
        Objects.requireNonNull(key, "key");
        if (start < 0) {
            throw new IllegalArgumentException("start must be >= 0");
        }
        if (stop < start) {
            throw new IllegalArgumentException("stop must be >= start");
        }
        return requireCommands().zrevrangeWithScores(key.value(), start, stop).thenApply(scoredValues -> {
            if (scoredValues == null || scoredValues.isEmpty()) {
                return List.of();
            }
            List<RankEntry> entries = new ArrayList<>(scoredValues.size());
            long rank = start + 1;
            for (ScoredValue<String> sv : scoredValues) {
                if (sv != null && sv.hasValue()) {
                    entries.add(new RankEntry(sv.getValue(), sv.getScore(), rank));
                    rank++;
                }
            }
            return Collections.unmodifiableList(entries);
        });
    }

    @Override
    public CompletionStage<Boolean> removeAsync(RedisKey key, String member) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(member, "member");
        return requireCommands().zrem(key.value(), member).thenApply(count -> count != null && count > 0);
    }

    @Override
    public CompletionStage<Long> sizeAsync(RedisKey key) {
        Objects.requireNonNull(key, "key");
        return requireCommands().zcard(key.value()).thenApply(count -> {
            if (count == null) {
                return 0L;
            }
            return count;
        });
    }
}
