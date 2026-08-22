package com.cotani.redis.channel;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable wrapper around a typed message received from a Redis Pub/Sub channel.
 *
 * @param channel the channel where the message was received
 * @param payload the deserialized message body
 * @param timestamp the instant the message was processed locally
 * @param <T> message payload type
 */
public record ChannelMessage<T>(ChannelId channel, T payload, Instant timestamp) {

    public ChannelMessage {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public static <T> ChannelMessage<T> of(ChannelId channel, T payload) {
        return new ChannelMessage<>(channel, payload, Instant.now());
    }
}
