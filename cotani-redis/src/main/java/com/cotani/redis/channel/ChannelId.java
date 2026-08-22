package com.cotani.redis.channel;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing a unique Redis Pub/Sub channel identifier.
 *
 * @param value the channel name string
 */
public record ChannelId(String value) {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_.:\\-]+$");

    public ChannelId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Channel name must not be blank");
        }
        if (!VALID_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException("Channel name contains invalid characters: " + value);
        }
    }

    public static ChannelId of(String value) {
        return new ChannelId(value);
    }
}
