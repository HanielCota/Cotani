package com.cotani.redis.store;

import com.cotani.redis.codec.RedisCodec;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * High-performance non-blocking Redis key-value operations and atomic counters.
 */
public interface RedisKeyValueStore {

    /**
     * Retrieves a string value by key.
     *
     * @param key target key
     * @return stage completing with value if present, or empty if missing
     */
    CompletionStage<Optional<String>> getAsync(RedisKey key);

    /**
     * Retrieves raw binary bytes by key.
     *
     * @param key target key
     * @return stage completing with byte array if present, or empty if missing
     */
    CompletionStage<Optional<byte[]>> getBinaryAsync(RedisKey key);

    /**
     * Retrieves a strongly typed object deserialized using the specified codec.
     *
     * @param key target key
     * @param codec codec to decode stored bytes
     * @param <T> value type
     * @return stage completing with decoded value if present, or empty if missing
     */
    <T> CompletionStage<Optional<T>> getAsync(RedisKey key, RedisCodec<T> codec);

    /**
     * Sets a string value with no expiration.
     *
     * @param key target key
     * @param value value string
     * @return stage completing once written
     */
    CompletionStage<Void> setAsync(RedisKey key, String value);

    /**
     * Sets a string value with a time-to-live expiration.
     *
     * @param key target key
     * @param value value string
     * @param ttl duration before key expires
     * @return stage completing once written
     */
    CompletionStage<Void> setAsync(RedisKey key, String value, Duration ttl);

    /**
     * Sets a string value with a time-to-live expiration only if the key does not already exist (SET NX PX).
     *
     * @param key target key
     * @param value value string
     * @param ttl duration before key expires
     * @return stage completing with true if key was set, false if key already existed
     */
    CompletionStage<Boolean> setIfAbsentAsync(RedisKey key, String value, Duration ttl);

    /**
     * Sets a typed object encoded using the specified codec with no expiration.
     *
     * @param key target key
     * @param value value object
     * @param codec codec to encode the object
     * @param <T> value type
     * @return stage completing once written
     */
    <T> CompletionStage<Void> setAsync(RedisKey key, T value, RedisCodec<T> codec);

    /**
     * Sets a typed object encoded using the specified codec with a time-to-live expiration.
     *
     * @param key target key
     * @param value value object
     * @param codec codec to encode the object
     * @param ttl duration before key expires
     * @param <T> value type
     * @return stage completing once written
     */
    <T> CompletionStage<Void> setAsync(RedisKey key, T value, RedisCodec<T> codec, Duration ttl);

    /**
     * Deletes a key from Redis.
     *
     * @param key target key
     * @return stage completing with true if key existed and was deleted, false otherwise
     */
    CompletionStage<Boolean> deleteAsync(RedisKey key);

    /**
     * Checks if a key exists in Redis.
     *
     * @param key target key
     * @return stage completing with true if key exists, false otherwise
     */
    CompletionStage<Boolean> existsAsync(RedisKey key);

    /**
     * Sets or updates expiration on an existing key.
     *
     * @param key target key
     * @param ttl time to live
     * @return stage completing with true if expiration was set, false if key does not exist
     */
    CompletionStage<Boolean> expireAsync(RedisKey key, Duration ttl);

    /**
     * Atomically increments the numeric value at key by delta.
     *
     * @param key target key
     * @param delta amount to add (can be negative)
     * @return stage completing with the value after increment
     */
    CompletionStage<Long> incrementAndGetAsync(RedisKey key, long delta);

    /**
     * Atomically decrements the numeric value at key by delta.
     *
     * @param key target key
     * @param delta amount to subtract
     * @return stage completing with the value after decrement
     */
    CompletionStage<Long> decrementAndGetAsync(RedisKey key, long delta);

    /**
     * Finds keys matching a pattern using the blocking KEYS command.
     * Use sparingly or prefer {@link #scanKeysAsync(String)} on production clusters.
     *
     * @param pattern glob-style pattern (e.g. "player:*:cooldown")
     * @return stage completing with matching keys
     */
    CompletionStage<Set<RedisKey>> keysAsync(String pattern);

    /**
     * Asynchronously scans for keys matching a pattern using cursor-based non-blocking SCAN.
     * Safe for production clusters with large datasets.
     *
     * @param pattern glob pattern (e.g. "user:*")
     * @return stage completing with set of matching keys
     */
    CompletionStage<Set<RedisKey>> scanKeysAsync(String pattern);

    /**
     * Retrieves the value of a field within a hash at key.
     *
     * @param key target hash key
     * @param field field name
     * @return stage completing with value if present, or empty if missing
     */
    CompletionStage<Optional<String>> hgetAsync(RedisKey key, String field);

    /**
     * Retrieves raw binary bytes of a field within a hash at key.
     *
     * @param key target hash key
     * @param field field bytes
     * @return stage completing with bytes if present, or empty if missing
     */
    CompletionStage<Optional<byte[]>> hgetBinaryAsync(RedisKey key, byte[] field);

    /**
     * Retrieves and decodes a typed object from a field within a hash.
     *
     * @param key target hash key
     * @param field field name
     * @param codec codec to decode stored bytes
     * @param <T> value type
     * @return stage completing with decoded value if present, or empty if missing
     */
    <T> CompletionStage<Optional<T>> hgetAsync(RedisKey key, String field, RedisCodec<T> codec);

    /**
     * Sets a field and value within a hash at key.
     *
     * @param key target hash key
     * @param field field name
     * @param value field value
     * @return stage completing once written
     */
    CompletionStage<Void> hsetAsync(RedisKey key, String field, String value);

    /**
     * Sets a field and raw binary value within a hash at key.
     *
     * @param key target hash key
     * @param field field bytes
     * @param value value bytes
     * @return stage completing once written
     */
    CompletionStage<Void> hsetBinaryAsync(RedisKey key, byte[] field, byte[] value);

    /**
     * Sets a typed object encoded using the specified codec within a hash field.
     *
     * @param key target hash key
     * @param field field name
     * @param value value object
     * @param codec codec to encode the object
     * @param <T> value type
     * @return stage completing once written
     */
    <T> CompletionStage<Void> hsetAsync(RedisKey key, String field, T value, RedisCodec<T> codec);

    /**
     * Retrieves all fields and values of a hash.
     *
     * @param key target hash key
     * @return stage completing with map of all key-value pairs
     */
    CompletionStage<java.util.Map<String, String>> hgetAllAsync(RedisKey key);

    /**
     * Deletes one or more fields from a hash.
     *
     * @param key target hash key
     * @param fields fields to delete
     * @return stage completing with true if at least one field was removed, false otherwise
     */
    CompletionStage<Boolean> hdelAsync(RedisKey key, String... fields);

    /**
     * Checks if a field exists within a hash.
     *
     * @param key target hash key
     * @param field field name
     * @return stage completing with true if field exists, false otherwise
     */
    CompletionStage<Boolean> hexistsAsync(RedisKey key, String field);

    /**
     * Atomically increments the numeric value of a hash field by delta.
     *
     * @param key target hash key
     * @param field field name
     * @param delta amount to increment
     * @return stage completing with field value after increment
     */
    CompletionStage<Long> hincrByAsync(RedisKey key, String field, long delta);

    /**
     * Retrieves all field names within a hash.
     *
     * @param key target hash key
     * @return stage completing with set of field names
     */
    CompletionStage<Set<String>> hkeysAsync(RedisKey key);

    /**
     * Returns the number of fields within a hash.
     *
     * @param key target hash key
     * @return stage completing with the number of fields
     */
    CompletionStage<Long> hlenAsync(RedisKey key);
}
