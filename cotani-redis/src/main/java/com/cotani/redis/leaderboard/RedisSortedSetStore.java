package com.cotani.redis.leaderboard;

import com.cotani.redis.store.RedisKey;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Non-blocking Redis Sorted Set (ZSET) operations for real-time leaderboards,
 * ranked statistics, and priority scoring.
 */
public interface RedisSortedSetStore {

    /**
     * Adds or updates the score of a member in the sorted set.
     *
     * @param key sorted set key
     * @param member member identifier
     * @param score numeric score
     * @return stage completing with true if member was newly added, false if updated
     */
    CompletionStage<Boolean> addOrUpdateScoreAsync(RedisKey key, String member, double score);

    /**
     * Atomically increments the score of a member by delta.
     *
     * @param key sorted set key
     * @param member member identifier
     * @param delta amount to increment
     * @return stage completing with new score after increment
     */
    CompletionStage<Double> incrementScoreAsync(RedisKey key, String member, double delta);

    /**
     * Retrieves the score of a member.
     *
     * @param key sorted set key
     * @param member member identifier
     * @return stage completing with score if present, or empty if member is not in the set
     */
    CompletionStage<Optional<Double>> getScoreAsync(RedisKey key, String member);

    /**
     * Retrieves the 1-based rank of a member ordered by score descending (highest score = rank 1).
     *
     * @param key sorted set key
     * @param member member identifier
     * @return stage completing with 1-based rank if present, or empty if member is not in the set
     */
    CompletionStage<Optional<Long>> getRankAsync(RedisKey key, String member);

    /**
     * Retrieves the top ranked entries ordered by score descending (highest score first).
     *
     * @param key sorted set key
     * @param limit maximum number of entries to return (must be > 0)
     * @return stage completing with list of ranked entries
     */
    CompletionStage<List<RankEntry>> getTopAsync(RedisKey key, int limit);

    /**
     * Retrieves a range of ranked entries between start and stop rank (0-based inclusive, ordered by score descending).
     *
     * @param key sorted set key
     * @param start 0-based start offset
     * @param stop 0-based stop offset
     * @return stage completing with list of ranked entries
     */
    CompletionStage<List<RankEntry>> getRangeAsync(RedisKey key, long start, long stop);

    /**
     * Removes a member from the sorted set.
     *
     * @param key sorted set key
     * @param member member identifier
     * @return stage completing with true if member was removed, false if not present
     */
    CompletionStage<Boolean> removeAsync(RedisKey key, String member);

    /**
     * Returns the total cardinality (number of members) of the sorted set.
     *
     * @param key sorted set key
     * @return stage completing with total number of members
     */
    CompletionStage<Long> sizeAsync(RedisKey key);
}
