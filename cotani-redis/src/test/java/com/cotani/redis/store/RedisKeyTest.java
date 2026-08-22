package com.cotani.redis.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RedisKeyTest {

    @Test
    void shouldCreateRedisKey() {
        var k1 = RedisKey.of("user:123:session");
        var k2 = RedisKey.of("user:123:session");
        var other = RedisKey.of("user:456:session");

        assertEquals("user:123:session", k1.value());
        assertEquals(k1, k2);
        assertEquals(k1.hashCode(), k2.hashCode());
        assertNotEquals(k1, other);
    }

    @Test
    void shouldRejectBlankRedisKey() {
        assertThrows(IllegalArgumentException.class, () -> RedisKey.of(""));
        assertThrows(IllegalArgumentException.class, () -> RedisKey.of("   "));
    }
}
