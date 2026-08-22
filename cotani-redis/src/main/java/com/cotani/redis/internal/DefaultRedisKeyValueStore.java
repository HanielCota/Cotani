package com.cotani.redis.internal;

import com.cotani.api.InternalApi;
import com.cotani.redis.codec.RedisCodec;
import com.cotani.redis.store.RedisKey;
import com.cotani.redis.store.RedisKeyValueStore;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link RedisKeyValueStore}.
 */
@InternalApi
public final class DefaultRedisKeyValueStore implements RedisKeyValueStore {

    private static final String KEY_PARAM = "key";
    private static final String VALUE_PARAM = "value";
    private static final String CODEC_PARAM = "codec";
    private static final String FIELD_PARAM = "field";
    private static final String TTL_PARAM = "ttl";

    private final Supplier<StatefulRedisConnection<String, String>> stringConnectionSupplier;
    private final Supplier<StatefulRedisConnection<byte[], byte[]>> binaryConnectionSupplier;

    public DefaultRedisKeyValueStore(
            Supplier<StatefulRedisConnection<String, String>> stringConnectionSupplier,
            Supplier<StatefulRedisConnection<byte[], byte[]>> binaryConnectionSupplier) {
        this.stringConnectionSupplier = Objects.requireNonNull(stringConnectionSupplier, "stringConnectionSupplier");
        this.binaryConnectionSupplier = Objects.requireNonNull(binaryConnectionSupplier, "binaryConnectionSupplier");
    }

    private RedisAsyncCommands<String, String> requireCommands() {
        var connection = stringConnectionSupplier.get();
        if (connection == null) {
            throw new IllegalStateException("Redis connection is not active");
        }
        return connection.async();
    }

    private io.lettuce.core.api.async.RedisAsyncCommands<byte[], byte[]> requireBinaryCommands() {
        var connection = binaryConnectionSupplier.get();
        if (connection == null) {
            throw new IllegalStateException("Redis binary connection is not active");
        }
        return connection.async();
    }

    @Override
    public CompletionStage<Optional<String>> getAsync(RedisKey key) {
        Objects.requireNonNull(key, KEY_PARAM);
        var commands = stringConnectionSupplier.get().async();
        return commands.get(key.value()).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletionStage<Optional<byte[]>> getBinaryAsync(RedisKey key) {
        Objects.requireNonNull(key, KEY_PARAM);
        var commands = binaryConnectionSupplier.get().async();
        byte[] keyBytes = key.value().getBytes(StandardCharsets.UTF_8);
        return commands.get(keyBytes).thenApply(Optional::ofNullable);
    }

    @Override
    public <T> CompletionStage<Optional<T>> getAsync(RedisKey key, RedisCodec<T> codec) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(codec, CODEC_PARAM);

        return getBinaryAsync(key)
                .thenApply(optionalBytes ->
                        optionalBytes.map(bytes -> Objects.requireNonNull(codec.decode(bytes), "decoded object")));
    }

    @Override
    public CompletionStage<Void> setAsync(RedisKey key, String value) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(value, VALUE_PARAM);
        var commands = stringConnectionSupplier.get().async();
        return commands.set(key.value(), value).thenApply(_ -> (Void) null);
    }

    @Override
    public CompletionStage<Void> setAsync(RedisKey key, String value, Duration ttl) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(value, VALUE_PARAM);
        Objects.requireNonNull(ttl, TTL_PARAM);
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }

        var commands = stringConnectionSupplier.get().async();
        var setArgs = SetArgs.Builder.px(ttl.toMillis());
        return commands.set(key.value(), value, setArgs).thenApply(_ -> (Void) null);
    }

    @Override
    public CompletionStage<Boolean> setIfAbsentAsync(RedisKey key, String value, Duration ttl) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(value, VALUE_PARAM);
        Objects.requireNonNull(ttl, TTL_PARAM);
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }

        var commands = stringConnectionSupplier.get().async();
        var setArgs = SetArgs.Builder.nx().px(ttl.toMillis());
        return commands.set(key.value(), value, setArgs).thenApply("OK"::equalsIgnoreCase);
    }

    @Override
    public <T> CompletionStage<Void> setAsync(RedisKey key, T value, RedisCodec<T> codec) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(value, VALUE_PARAM);
        Objects.requireNonNull(codec, CODEC_PARAM);

        byte[] keyBytes = key.value().getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = Objects.requireNonNull(codec.encode(value), "encoded value bytes");

        var commands = binaryConnectionSupplier.get().async();
        return commands.set(keyBytes, valueBytes).thenApply(_ -> (Void) null);
    }

    @Override
    public <T> CompletionStage<Void> setAsync(RedisKey key, T value, RedisCodec<T> codec, Duration ttl) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(value, VALUE_PARAM);
        Objects.requireNonNull(codec, CODEC_PARAM);
        Objects.requireNonNull(ttl, TTL_PARAM);
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }

        byte[] keyBytes = key.value().getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = Objects.requireNonNull(codec.encode(value), "encoded value bytes");

        var commands = binaryConnectionSupplier.get().async();
        var setArgs = SetArgs.Builder.px(ttl.toMillis());
        return commands.set(keyBytes, valueBytes, setArgs).thenApply(_ -> (Void) null);
    }

    @Override
    public CompletionStage<Boolean> deleteAsync(RedisKey key) {
        Objects.requireNonNull(key, KEY_PARAM);
        var commands = stringConnectionSupplier.get().async();
        return commands.del(key.value()).thenApply(count -> count != null && count > 0);
    }

    @Override
    public CompletionStage<Boolean> existsAsync(RedisKey key) {
        Objects.requireNonNull(key, KEY_PARAM);
        var commands = stringConnectionSupplier.get().async();
        return commands.exists(key.value()).thenApply(count -> count != null && count > 0);
    }

    @Override
    public CompletionStage<Boolean> expireAsync(RedisKey key, Duration ttl) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(ttl, TTL_PARAM);
        var commands = stringConnectionSupplier.get().async();
        return commands.pexpire(key.value(), ttl.toMillis()).thenApply(success -> Boolean.TRUE.equals(success));
    }

    @Override
    public CompletionStage<Long> incrementAndGetAsync(RedisKey key, long delta) {
        Objects.requireNonNull(key, KEY_PARAM);
        var commands = stringConnectionSupplier.get().async();
        return commands.incrby(key.value(), delta);
    }

    @Override
    public CompletionStage<Long> decrementAndGetAsync(RedisKey key, long delta) {
        Objects.requireNonNull(key, KEY_PARAM);
        var commands = stringConnectionSupplier.get().async();
        return commands.decrby(key.value(), delta);
    }

    @Override
    public CompletionStage<Set<RedisKey>> keysAsync(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        var commands = stringConnectionSupplier.get().async();
        return commands.keys(pattern).thenApply(keys -> {
            if (keys == null || keys.isEmpty()) {
                return Set.of();
            }
            return keys.stream().map(RedisKey::of).collect(Collectors.toUnmodifiableSet());
        });
    }

    @Override
    public CompletionStage<Set<RedisKey>> scanKeysAsync(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        var commands = stringConnectionSupplier.get().async();
        var scanArgs = ScanArgs.Builder.matches(pattern).limit(100);
        Set<RedisKey> accumulated = ConcurrentHashMap.newKeySet();
        return iterateScan(commands, ScanCursor.INITIAL, scanArgs, accumulated);
    }

    private CompletionStage<Set<RedisKey>> iterateScan(
            RedisAsyncCommands<String, String> commands,
            ScanCursor cursor,
            ScanArgs scanArgs,
            Set<RedisKey> accumulated) {
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(cursor, "cursor");
        Objects.requireNonNull(scanArgs, "scanArgs");
        Objects.requireNonNull(accumulated, "accumulated");

        return commands.scan(cursor, scanArgs).thenCompose(keyScanCursor -> {
            if (keyScanCursor == null) {
                return CompletableFuture.completedFuture(Collections.unmodifiableSet(accumulated));
            }
            if (keyScanCursor.getKeys() != null) {
                for (String keyStr : keyScanCursor.getKeys()) {
                    if (keyStr != null && !keyStr.isBlank()) {
                        accumulated.add(RedisKey.of(keyStr));
                    }
                }
            }
            if (keyScanCursor.isFinished() || keyScanCursor.getCursor() == null) {
                return CompletableFuture.completedFuture(Collections.unmodifiableSet(accumulated));
            }
            return iterateScan(commands, ScanCursor.of(keyScanCursor.getCursor()), scanArgs, accumulated);
        });
    }

    @Override
    public CompletionStage<Optional<String>> hgetAsync(RedisKey key, String field) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(field, FIELD_PARAM);
        return requireCommands().hget(key.value(), field).thenApply(Optional::ofNullable);
    }

    @Override
    public CompletionStage<Optional<byte[]>> hgetBinaryAsync(RedisKey key, byte[] field) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(field, FIELD_PARAM);
        byte[] keyBytes = key.value().getBytes(StandardCharsets.UTF_8);
        return requireBinaryCommands().hget(keyBytes, field).thenApply(Optional::ofNullable);
    }

    @Override
    public <T> CompletionStage<Optional<T>> hgetAsync(RedisKey key, String field, RedisCodec<T> codec) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(field, FIELD_PARAM);
        Objects.requireNonNull(codec, CODEC_PARAM);
        byte[] fieldBytes = field.getBytes(StandardCharsets.UTF_8);
        return hgetBinaryAsync(key, fieldBytes)
                .thenApply(optionalBytes ->
                        optionalBytes.map(bytes -> Objects.requireNonNull(codec.decode(bytes), "decoded hash object")));
    }

    @Override
    public CompletionStage<Void> hsetAsync(RedisKey key, String field, String value) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(field, FIELD_PARAM);
        Objects.requireNonNull(value, VALUE_PARAM);
        return requireCommands().hset(key.value(), field, value).thenApply(_ -> (Void) null);
    }

    @Override
    public CompletionStage<Void> hsetBinaryAsync(RedisKey key, byte[] field, byte[] value) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(field, FIELD_PARAM);
        Objects.requireNonNull(value, VALUE_PARAM);
        byte[] keyBytes = key.value().getBytes(StandardCharsets.UTF_8);
        return requireBinaryCommands().hset(keyBytes, field, value).thenApply(_ -> (Void) null);
    }

    @Override
    public <T> CompletionStage<Void> hsetAsync(RedisKey key, String field, T value, RedisCodec<T> codec) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(field, FIELD_PARAM);
        Objects.requireNonNull(value, VALUE_PARAM);
        Objects.requireNonNull(codec, CODEC_PARAM);
        byte[] fieldBytes = field.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = Objects.requireNonNull(codec.encode(value), "encoded value bytes");
        return hsetBinaryAsync(key, fieldBytes, valueBytes);
    }

    @Override
    public CompletionStage<Map<String, String>> hgetAllAsync(RedisKey key) {
        Objects.requireNonNull(key, KEY_PARAM);
        return requireCommands().hgetall(key.value()).thenApply(map -> {
            if (map == null || map.isEmpty()) {
                return Map.of();
            }
            return Collections.unmodifiableMap(map);
        });
    }

    @Override
    public CompletionStage<Boolean> hdelAsync(RedisKey key, String... fields) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(fields, "fields");
        return requireCommands().hdel(key.value(), fields).thenApply(count -> count != null && count > 0);
    }

    @Override
    public CompletionStage<Boolean> hexistsAsync(RedisKey key, String field) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(field, FIELD_PARAM);
        return requireCommands().hexists(key.value(), field).thenApply(exists -> Boolean.TRUE.equals(exists));
    }

    @Override
    public CompletionStage<Long> hincrByAsync(RedisKey key, String field, long delta) {
        Objects.requireNonNull(key, KEY_PARAM);
        Objects.requireNonNull(field, FIELD_PARAM);
        return requireCommands().hincrby(key.value(), field, delta).thenApply(val -> {
            if (val == null) {
                return 0L;
            }
            return val;
        });
    }

    @Override
    public CompletionStage<Set<String>> hkeysAsync(RedisKey key) {
        Objects.requireNonNull(key, KEY_PARAM);
        return requireCommands().hkeys(key.value()).thenApply(keys -> {
            if (keys == null || keys.isEmpty()) {
                return Set.of();
            }
            return Set.copyOf(keys);
        });
    }

    @Override
    public CompletionStage<Long> hlenAsync(RedisKey key) {
        Objects.requireNonNull(key, KEY_PARAM);
        return requireCommands().hlen(key.value()).thenApply(len -> {
            if (len == null) {
                return 0L;
            }
            return len;
        });
    }
}
