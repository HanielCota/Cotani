package com.cotani.redis.internal;

import com.cotani.api.InternalApi;
import com.cotani.redis.CotaniRedis;
import com.cotani.redis.RedisState;
import com.cotani.redis.channel.ChannelId;
import com.cotani.redis.channel.RedisChannel;
import com.cotani.redis.codec.RedisCodec;
import com.cotani.redis.config.RedisConfig;
import com.cotani.redis.exception.RedisConnectionException;
import com.cotani.redis.lock.DistributedLockService;
import com.cotani.redis.store.RedisKeyValueStore;
import com.cotani.task.api.PaperTaskScheduler;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Default internal implementation of {@link CotaniRedis} backed by Lettuce.
 */
@InternalApi
@SuppressWarnings("resource") // Stateful Redis connections are persistent lifecycle resources closed in closeAsync()
public final class DefaultCotaniRedis implements CotaniRedis {

    private static final String ID_PARAM = "id";
    private static final String LISTENER_PARAM = "listener";

    private final Plugin plugin;
    private final RedisConfig config;
    private final @Nullable PaperTaskScheduler scheduler;
    private final RedisClient redisClient;
    private final Executor defaultAsyncExecutor;

    private final ConcurrentHashMap<ChannelId, Set<Consumer<byte[]>>> channelListeners = new ConcurrentHashMap<>();

    private final AtomicReference<RedisState> state = new AtomicReference<>(RedisState.NEW);
    private @Nullable StatefulRedisConnection<String, String> commandsConnection;
    private @Nullable StatefulRedisConnection<byte[], byte[]> binaryConnection;
    private @Nullable StatefulRedisPubSubConnection<byte[], byte[]> pubSubConnection;

    private final DistributedLockService lockService;
    private final RedisKeyValueStore keyValueStore;
    private final com.cotani.redis.leaderboard.RedisSortedSetStore sortedSetStore;
    private final com.cotani.redis.ratelimit.RedisRateLimiter rateLimiter;
    private final java.util.concurrent.ScheduledExecutorService rpcFallbackExecutor;

    private @Nullable CompletableFuture<@Nullable Void> startFuture;
    private @Nullable CompletableFuture<@Nullable Void> closeFuture;

    public DefaultCotaniRedis(Plugin plugin, RedisConfig config, @Nullable PaperTaskScheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.config = Objects.requireNonNull(config, "config");
        this.scheduler = scheduler;

        var uriBuilder = RedisURI.builder()
                .withHost(config.host())
                .withPort(config.port())
                .withDatabase(config.database())
                .withSsl(config.ssl())
                .withClientName(config.clientName())
                .withTimeout(config.timeout());

        configureAuthentication(uriBuilder, config);

        this.redisClient = RedisClient.create(uriBuilder.build());
        this.redisClient.setOptions(ClientOptions.builder()
                .autoReconnect(true)
                .pingBeforeActivateConnection(true)
                .socketOptions(
                        SocketOptions.builder().connectTimeout(config.timeout()).build())
                .build());

        this.redisClient.addListener(new RedisConnectionStateListener() {
            @Override
            public void onRedisConnected(RedisChannelHandler<?, ?> connection, SocketAddress socketAddress) {
                var activePubSub = pubSubConnection;
                if (activePubSub == null || !Objects.equals(connection, activePubSub) || channelListeners.isEmpty()) {
                    return;
                }
                for (var channelId : channelListeners.keySet()) {
                    byte[] channelBytes = channelId.value().getBytes(StandardCharsets.UTF_8);
                    var _ = activePubSub.async().subscribe(channelBytes);
                }
            }

            @Override
            public void onRedisDisconnected(RedisChannelHandler<?, ?> connection) {
                // Intentionally empty: auto-reconnect handled by onRedisConnected
            }

            @Override
            public void onRedisExceptionCaught(RedisChannelHandler<?, ?> connection, Throwable cause) {
                // Intentionally empty: connection diagnostics handled by Lettuce internal logging
            }
        });

        this.defaultAsyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.rpcFallbackExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cotani-redis-rpc-fallback");
            thread.setDaemon(true);
            return thread;
        });

        this.lockService = new DefaultDistributedLockService(this::requireCommandsConnection, scheduler);
        this.keyValueStore =
                new DefaultRedisKeyValueStore(this::requireCommandsConnection, this::requireBinaryConnection);
        this.sortedSetStore = new DefaultRedisSortedSetStore(this::requireCommandsConnection);
        this.rateLimiter = new DefaultRedisRateLimiter(this::requireCommandsConnection);
    }

    private static void configureAuthentication(RedisURI.Builder uriBuilder, RedisConfig config) {
        if (config.password() == null) {
            return;
        }
        if (config.username() != null) {
            uriBuilder.withAuthentication(config.username(), config.password());
            return;
        }
        uriBuilder.withPassword(config.password().toCharArray());
    }

    public Plugin plugin() {
        return plugin;
    }

    public @Nullable PaperTaskScheduler scheduler() {
        return scheduler;
    }

    @Override
    public synchronized CompletionStage<Void> startAsync() {
        if (state.get() == RedisState.CONNECTED) {
            return CompletableFuture.completedFuture(null);
        }
        if (startFuture != null) {
            return startFuture;
        }

        state.set(RedisState.STARTING);
        var future = new CompletableFuture<@Nullable Void>();
        this.startFuture = future;

        var _ = CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                this.commandsConnection = redisClient.connect(StringCodec.UTF8);
                                this.binaryConnection = redisClient.connect(ByteArrayCodec.INSTANCE);
                                this.pubSubConnection = redisClient.connectPubSub(ByteArrayCodec.INSTANCE);

                                this.pubSubConnection.addListener(new RedisPubSubAdapter<>() {
                                    @Override
                                    public void message(byte[] channelBytes, byte[] messageBytes) {
                                        if (channelBytes == null || messageBytes == null) {
                                            return;
                                        }
                                        String channelName = new String(channelBytes, StandardCharsets.UTF_8);
                                        var channelId = ChannelId.of(channelName);
                                        var listeners = channelListeners.get(channelId);
                                        if (listeners == null) {
                                            return;
                                        }
                                        for (var listener : listeners) {
                                            if (listener != null) {
                                                listener.accept(messageBytes);
                                            }
                                        }
                                    }
                                });

                                state.set(RedisState.CONNECTED);
                                return null;
                            } catch (Exception e) {
                                state.set(RedisState.FAILED);
                                // Clean up partially established connections
                                if (pubSubConnection != null) {
                                    try {
                                        pubSubConnection.close();
                                    } catch (Exception closeError) {
                                        e.addSuppressed(closeError);
                                    }
                                    pubSubConnection = null;
                                }
                                if (binaryConnection != null) {
                                    try {
                                        binaryConnection.close();
                                    } catch (Exception closeError) {
                                        e.addSuppressed(closeError);
                                    }
                                    binaryConnection = null;
                                }
                                if (commandsConnection != null) {
                                    try {
                                        commandsConnection.close();
                                    } catch (Exception closeError) {
                                        e.addSuppressed(closeError);
                                    }
                                    commandsConnection = null;
                                }
                                throw new RedisConnectionException(
                                        "Failed to establish Redis connection to " + config.host() + ":"
                                                + config.port(),
                                        e);
                            }
                        },
                        defaultAsyncExecutor)
                .whenComplete((_, error) -> {
                    if (error != null) {
                        future.completeExceptionally(error);
                        return;
                    }
                    future.complete(null);
                });

        return future;
    }

    private StatefulRedisConnection<String, String> requireCommandsConnection() {
        var currentState = state.get();
        if (commandsConnection == null || currentState != RedisState.CONNECTED) {
            throw new IllegalStateException("Redis client is not connected. Current state: " + currentState);
        }
        return commandsConnection;
    }

    private StatefulRedisConnection<byte[], byte[]> requireBinaryConnection() {
        var currentState = state.get();
        if (binaryConnection == null || currentState != RedisState.CONNECTED) {
            throw new IllegalStateException("Redis client is not connected. Current state: " + currentState);
        }
        return binaryConnection;
    }

    private StatefulRedisPubSubConnection<byte[], byte[]> requirePubSubConnection() {
        var currentState = state.get();
        if (pubSubConnection == null || currentState != RedisState.CONNECTED) {
            throw new IllegalStateException("Redis PubSub is not connected. Current state: " + currentState);
        }
        return pubSubConnection;
    }

    @Override
    public <T> RedisChannel<T> channel(ChannelId id, RedisCodec<T> codec) {
        Objects.requireNonNull(id, ID_PARAM);
        Objects.requireNonNull(codec, "codec");

        return new DefaultRedisChannel<>(
                id, codec, this::publishRawAsync, this::subscribeRaw, this::unsubscribeRaw, defaultAsyncExecutor);
    }

    private CompletionStage<Long> publishRawAsync(ChannelId id, byte[] messageBytes) {
        Objects.requireNonNull(id, ID_PARAM);
        Objects.requireNonNull(messageBytes, "messageBytes");
        byte[] channelBytes = id.value().getBytes(StandardCharsets.UTF_8);
        return requirePubSubConnection().async().publish(channelBytes, messageBytes);
    }

    private void subscribeRaw(ChannelId id, Consumer<byte[]> listener) {
        Objects.requireNonNull(id, ID_PARAM);
        Objects.requireNonNull(listener, LISTENER_PARAM);
        var listeners = channelListeners.computeIfAbsent(id, _ -> ConcurrentHashMap.newKeySet());
        boolean wasEmpty = listeners.isEmpty();
        listeners.add(listener);

        if (!wasEmpty) {
            return;
        }
        byte[] channelBytes = id.value().getBytes(StandardCharsets.UTF_8);
        var _ = requirePubSubConnection().async().subscribe(channelBytes);
    }

    private void unsubscribeRaw(ChannelId id, Consumer<byte[]> listener) {
        Objects.requireNonNull(id, ID_PARAM);
        Objects.requireNonNull(listener, LISTENER_PARAM);
        var listeners = channelListeners.get(id);
        if (listeners == null) {
            return;
        }
        listeners.remove(listener);
        if (!listeners.isEmpty()) {
            return;
        }
        channelListeners.remove(id);
        byte[] channelBytes = id.value().getBytes(StandardCharsets.UTF_8);
        if (pubSubConnection != null && state.get() == RedisState.CONNECTED) {
            var _ = pubSubConnection.async().unsubscribe(channelBytes);
        }
    }

    @Override
    public <Q, R> com.cotani.redis.channel.RedisRpcChannel<Q, R> rpcChannel(
            ChannelId id, RedisCodec<Q> requestCodec, RedisCodec<R> responseCodec) {
        Objects.requireNonNull(id, ID_PARAM);
        Objects.requireNonNull(requestCodec, "requestCodec");
        Objects.requireNonNull(responseCodec, "responseCodec");
        return new DefaultRedisRpcChannel<>(id, requestCodec, responseCodec, this, scheduler, rpcFallbackExecutor);
    }

    @Override
    public DistributedLockService locks() {
        return lockService;
    }

    @Override
    public RedisKeyValueStore store() {
        return keyValueStore;
    }

    @Override
    public com.cotani.redis.leaderboard.RedisSortedSetStore sortedSets() {
        return sortedSetStore;
    }

    @Override
    public com.cotani.redis.ratelimit.RedisRateLimiter rateLimiter() {
        return rateLimiter;
    }

    @Override
    public CompletionStage<Boolean> pingAsync() {
        return requireCommandsConnection().async().ping().thenApply(pong -> "PONG".equalsIgnoreCase(pong));
    }

    @Override
    public RedisState state() {
        return Objects.requireNonNull(state.get(), "state");
    }

    @Override
    public synchronized CompletionStage<Void> closeAsync() {
        if (state.get() == RedisState.CLOSED) {
            return CompletableFuture.completedFuture(null);
        }
        if (closeFuture != null) {
            return closeFuture;
        }

        state.set(RedisState.CLOSING);
        var future = new CompletableFuture<@Nullable Void>();
        this.closeFuture = future;

        channelListeners.clear();
        rpcFallbackExecutor.shutdown();

        CompletableFuture<?> pubSubClose =
                pubSubConnection != null ? pubSubConnection.closeAsync() : CompletableFuture.completedFuture(null);
        CompletableFuture<?> binaryClose =
                binaryConnection != null ? binaryConnection.closeAsync() : CompletableFuture.completedFuture(null);
        CompletableFuture<?> commandsClose =
                commandsConnection != null ? commandsConnection.closeAsync() : CompletableFuture.completedFuture(null);

        var _ = CompletableFuture.allOf(pubSubClose, binaryClose, commandsClose)
                .thenCompose(_ -> redisClient.shutdownAsync())
                .whenComplete((_, _) -> {
                    state.set(RedisState.CLOSED);
                    future.complete(null);
                });

        return future;
    }

    @Override
    public void close() {
        closeAsync().whenComplete((_, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to close Cotani Redis", failure);
            }
        });
    }
}
