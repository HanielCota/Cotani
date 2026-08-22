package com.cotani.redis.lock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LockKeyAndTokenTest {

    @Test
    void shouldCreateValidLockKey() {
        var key1 = LockKey.of("player:balance:123");
        var key2 = LockKey.of("player:balance:123");
        var other = LockKey.of("other:key");

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1, other);
    }

    @Test
    void shouldRejectInvalidLockKey() {
        assertThrows(IllegalArgumentException.class, () -> LockKey.of(""));
        assertThrows(IllegalArgumentException.class, () -> LockKey.of("   "));
        assertThrows(IllegalArgumentException.class, () -> LockKey.of("key with space"));
    }

    @Test
    void shouldCreateLockToken() {
        var token1 = LockToken.random();
        var token2 = LockToken.of("custom-token");

        assertNotNull(token1.value());
        assertFalse(token1.value().isBlank());
        assertEquals("custom-token", token2.value());
    }

    @Test
    void shouldRejectBlankToken() {
        assertThrows(IllegalArgumentException.class, () -> LockToken.of(""));
        assertThrows(IllegalArgumentException.class, () -> LockToken.of("   "));
    }
}
