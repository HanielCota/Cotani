package com.cotani.redis.channel;

import com.cotani.redis.codec.RedisCodec;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Strongly typed Redis Pub/Sub message channel.
 *
 * @param <T> the payload message type
 */
public interface RedisChannel<T> {

    /**
     * Returns the channel identifier.
     *
     * @return channel identifier
     */
    ChannelId id();

    /**
     * Returns the codec used to serialize and deserialize messages on this channel.
     *
     * @return message codec
     */
    RedisCodec<T> codec();

    /**
     * Asynchronously publishes a message to this channel.
     *
     * @param message message payload to encode and broadcast
     * @return stage completing with the number of Redis clients that received the message
     */
    CompletionStage<Long> publishAsync(T message);

    /**
     * Subscribes a listener to receive messages from this channel on default virtual/async thread.
     *
     * @param listener message receiver callback
     * @return active subscription handle
     */
    ChannelSubscription subscribe(Consumer<T> listener);

    /**
     * Subscribes a listener to receive messages from this channel dispatched via an explicit executor.
     *
     * @param listener message receiver callback
     * @param executor executor to dispatch incoming message callbacks
     * @return active subscription handle
     */
    ChannelSubscription subscribe(Consumer<T> listener, Executor executor);
}
