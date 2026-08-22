package com.cotani.redis.codec;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Function;

/**
 * Strategy interface for encoding domain messages to bytes and decoding raw bytes back to domain objects.
 *
 * @param <T> payload type
 */
public interface RedisCodec<T> {

    /**
     * Serializes a message object to a byte array.
     *
     * @param message non-null message
     * @return non-null byte array
     */
    byte[] encode(T message);

    /**
     * Deserializes a byte array into a domain object.
     *
     * @param bytes non-null raw bytes
     * @return non-null domain object
     */
    T decode(byte[] bytes);

    /**
     * Codec for plain UTF-8 strings.
     *
     * @return string codec
     */
    static RedisCodec<String> string() {
        return StringRedisCodec.INSTANCE;
    }

    /**
     * Pass-through codec for raw byte arrays.
     *
     * @return byte array codec
     */
    static RedisCodec<byte[]> byteArray() {
        return ByteArrayRedisCodec.INSTANCE;
    }

    /**
     * Creates a codec from custom functional encoder and decoder mappings.
     *
     * @param encoder mapping function from T to byte[]
     * @param decoder mapping function from byte[] to T
     * @param <T> payload type
     * @return custom codec
     */
    static <T> RedisCodec<T> of(Function<T, byte[]> encoder, Function<byte[], T> decoder) {
        Objects.requireNonNull(encoder, "encoder");
        Objects.requireNonNull(decoder, "decoder");

        return new RedisCodec<>() {
            @Override
            public byte[] encode(T message) {
                Objects.requireNonNull(message, "message");
                return Objects.requireNonNull(encoder.apply(message), "encoded bytes");
            }

            @Override
            public T decode(byte[] bytes) {
                Objects.requireNonNull(bytes, "bytes");
                return Objects.requireNonNull(decoder.apply(bytes), "decoded object");
            }
        };
    }

    /**
     * Creates a text-based codec that transforms to and from UTF-8 strings.
     *
     * @param toString serializer function
     * @param fromString parser function
     * @param <T> payload type
     * @return string-backed codec
     */
    static <T> RedisCodec<T> text(Function<T, String> toString, Function<String, T> fromString) {
        Objects.requireNonNull(toString, "toString");
        Objects.requireNonNull(fromString, "fromString");

        return of(
                message -> Objects.requireNonNull(toString.apply(message), "serialized string")
                        .getBytes(StandardCharsets.UTF_8),
                bytes -> Objects.requireNonNull(
                        fromString.apply(new String(bytes, StandardCharsets.UTF_8)), "parsed object"));
    }
}
