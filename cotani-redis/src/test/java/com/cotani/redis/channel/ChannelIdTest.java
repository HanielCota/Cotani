package com.cotani.redis.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ChannelIdTest {

    @Test
    void shouldAcceptValidChannelNames() {
        var c1 = ChannelId.of("network:chat");
        var c2 = ChannelId.of("server.1-status");
        var c3 = ChannelId.of("alerts_2026");

        assertEquals("network:chat", c1.value());
        assertEquals("server.1-status", c2.value());
        assertEquals("alerts_2026", c3.value());
    }

    @Test
    void shouldHaveValueSemantics() {
        var c1 = ChannelId.of("network:chat");
        var c2 = ChannelId.of("network:chat");
        var other = ChannelId.of("network:other");

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertNotEquals(c1, other);
    }

    @Test
    void shouldRejectInvalidNames() {
        assertThrows(IllegalArgumentException.class, () -> ChannelId.of(""));
        assertThrows(IllegalArgumentException.class, () -> ChannelId.of("   "));
        assertThrows(IllegalArgumentException.class, () -> ChannelId.of("invalid space"));
        assertThrows(IllegalArgumentException.class, () -> ChannelId.of("invalid$symbol"));
    }
}
