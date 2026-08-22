package com.cotani.redis.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RedisCodecTest {

    @Test
    void shouldEncodeAndDecodeStrings() {
        var codec = RedisCodec.string();
        var message = "Hello Redis 2026!";

        byte[] encoded = codec.encode(message);
        String decoded = codec.decode(encoded);

        assertEquals(message, decoded);
    }

    @Test
    void shouldEncodeAndDecodeByteArrays() {
        var codec = RedisCodec.byteArray();
        byte[] original = new byte[] {1, 2, 3, 4, 5};

        byte[] encoded = codec.encode(original);
        byte[] decoded = codec.decode(encoded);

        assertArrayEquals(original, decoded);
    }

    @Test
    void shouldCreateCustomTextCodec() {
        record UserDto(String name, int age) {}

        var codec = RedisCodec.text(dto -> dto.name() + ":" + dto.age(), text -> {
            int colonIndex = text.indexOf(':');
            String name = text.substring(0, colonIndex);
            int age = Integer.parseInt(text.substring(colonIndex + 1));
            return new UserDto(name, age);
        });

        var user = new UserDto("Alex", 25);
        byte[] encoded = codec.encode(user);
        UserDto decoded = codec.decode(encoded);

        assertEquals(user, decoded);
    }
}
