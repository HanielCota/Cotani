package com.cotani.redis.codec;

import java.util.Objects;

/**
 * Direct byte array pass-through Redis codec.
 */
public final class ByteArrayRedisCodec implements RedisCodec<byte[]> {

    public static final ByteArrayRedisCodec INSTANCE = new ByteArrayRedisCodec();

    private ByteArrayRedisCodec() {
        // Singleton pattern instance constructor
    }

    @Override
    public byte[] encode(byte[] message) {
        Objects.requireNonNull(message, "message");
        return message.clone();
    }

    @Override
    public byte[] decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return bytes.clone();
    }
}
