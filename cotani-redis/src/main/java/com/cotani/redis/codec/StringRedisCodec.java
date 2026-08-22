package com.cotani.redis.codec;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * UTF-8 String Redis codec.
 */
public final class StringRedisCodec implements RedisCodec<String> {

    public static final StringRedisCodec INSTANCE = new StringRedisCodec();

    private StringRedisCodec() {
        // Singleton pattern instance constructor
    }

    @Override
    public byte[] encode(String message) {
        Objects.requireNonNull(message, "message");
        return message.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
