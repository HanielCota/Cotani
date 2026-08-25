package com.cotani.redis.internal;

import com.cotani.api.InternalApi;
import com.cotani.redis.channel.ChannelId;
import com.cotani.redis.channel.ChannelSubscription;
import com.cotani.redis.channel.RedisChannel;
import com.cotani.redis.codec.RedisCodec;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Default implementation of {@link RedisChannel}.
 *
 * @param <T> payload type
 */
@InternalApi
public final class DefaultRedisChannel<T> implements RedisChannel<T> {

    private final ChannelId id;
    private final RedisCodec<T> codec;
    private final BiFunction<ChannelId, byte[], CompletionStage<Long>> publishFunction;
    private final BiConsumer<ChannelId, Consumer<byte[]>> subscribeConsumer;
    private final BiConsumer<ChannelId, Consumer<byte[]>> unsubscribeConsumer;
    private final Executor defaultExecutor;

    public DefaultRedisChannel(
            ChannelId id,
            RedisCodec<T> codec,
            BiFunction<ChannelId, byte[], CompletionStage<Long>> publishFunction,
            BiConsumer<ChannelId, Consumer<byte[]>> subscribeConsumer,
            BiConsumer<ChannelId, Consumer<byte[]>> unsubscribeConsumer,
            Executor defaultExecutor) {
        this.id = Objects.requireNonNull(id, "id");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.publishFunction = Objects.requireNonNull(publishFunction, "publishFunction");
        this.subscribeConsumer = Objects.requireNonNull(subscribeConsumer, "subscribeConsumer");
        this.unsubscribeConsumer = Objects.requireNonNull(unsubscribeConsumer, "unsubscribeConsumer");
        this.defaultExecutor = Objects.requireNonNull(defaultExecutor, "defaultExecutor");
    }

    @Override
    public ChannelId id() {
        return id;
    }

    @Override
    public RedisCodec<T> codec() {
        return codec;
    }

    @Override
    public CompletionStage<Long> publishAsync(T message) {
        Objects.requireNonNull(message, "message");
        byte[] encoded = Objects.requireNonNull(codec.encode(message), "encoded message bytes");
        return publishFunction.apply(id, encoded);
    }

    @Override
    public ChannelSubscription subscribe(Consumer<T> listener) {
        return subscribe(listener, defaultExecutor);
    }

    @Override
    public ChannelSubscription subscribe(Consumer<T> listener, Executor executor) {
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(executor, "executor");

        Consumer<byte[]> rawListener = bytes -> {
            if (bytes == null) {
                return;
            }
            try {
                T decoded = Objects.requireNonNull(codec.decode(bytes), "decoded message");
                executor.execute(() -> {
                    try {
                        listener.accept(decoded);
                    } catch (Exception exception) {
                        java.util.logging.Logger.getLogger(DefaultRedisChannel.class.getName())
                                .log(java.util.logging.Level.WARNING, "Redis channel listener failed", exception);
                    }
                });
            } catch (Exception exception) {
                java.util.logging.Logger.getLogger(DefaultRedisChannel.class.getName())
                        .log(java.util.logging.Level.FINE, "Could not decode Redis channel frame", exception);
            }
        };

        subscribeConsumer.accept(id, rawListener);

        return new DefaultChannelSubscription(id, rawListener, unsubscribeConsumer);
    }

    private static final class DefaultChannelSubscription implements ChannelSubscription {
        private final ChannelId channelId;
        private final Consumer<byte[]> listener;
        private final BiConsumer<ChannelId, Consumer<byte[]>> unsubscribeConsumer;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private DefaultChannelSubscription(
                ChannelId channelId,
                Consumer<byte[]> listener,
                BiConsumer<ChannelId, Consumer<byte[]>> unsubscribeConsumer) {
            this.channelId = channelId;
            this.listener = listener;
            this.unsubscribeConsumer = unsubscribeConsumer;
        }

        @Override
        public ChannelId channelId() {
            return channelId;
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public CompletionStage<Void> unsubscribeAsync() {
            if (active.compareAndSet(true, false)) {
                unsubscribeConsumer.accept(channelId, listener);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            var _ = unsubscribeAsync();
        }
    }
}
