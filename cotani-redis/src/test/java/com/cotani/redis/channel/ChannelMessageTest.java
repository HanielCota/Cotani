package com.cotani.redis.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChannelMessageTest {

    @Test
    void shouldCreateChannelMessage() {
        var channel = ChannelId.of("network:chat");
        var msg = ChannelMessage.of(channel, "Hello world");

        assertEquals(channel, msg.channel());
        assertEquals("Hello world", msg.payload());
        assertNotNull(msg.timestamp());
    }

    @Test
    @SuppressWarnings({"NullAway", "DataFlowIssue", "ConstantConditions"})
    void shouldRejectNulls() {
        var channel = ChannelId.of("network:chat");

        assertThrows(NullPointerException.class, () -> new ChannelMessage<>(null, "test", Instant.now()));
        assertThrows(NullPointerException.class, () -> new ChannelMessage<>(channel, null, Instant.now()));
        assertThrows(NullPointerException.class, () -> new ChannelMessage<>(channel, "test", null));
    }
}
