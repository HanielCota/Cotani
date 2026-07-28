package com.cotani.gui.safety;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class ClickDebouncerTest {

    @Test
    void acceptsFirstClickAndDebouncesSubsequentClicks() throws InterruptedException {
        ClickDebouncer debouncer = new ClickDebouncer(Duration.ofMillis(100));
        UUID playerId = UUID.randomUUID();

        assertTrue(debouncer.tryAcquire(playerId));
        assertFalse(debouncer.tryAcquire(playerId));
    }

    @Test
    void releaseAllowsImmediateClick() {
        ClickDebouncer debouncer = new ClickDebouncer(Duration.ofMinutes(1));
        UUID playerId = UUID.randomUUID();

        assertTrue(debouncer.tryAcquire(playerId));
        assertFalse(debouncer.tryAcquire(playerId));

        debouncer.release(playerId);
        assertTrue(debouncer.tryAcquire(playerId));
    }

    @Test
    void clearResetsAllState() {
        ClickDebouncer debouncer = new ClickDebouncer(Duration.ofMinutes(1));
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        assertTrue(debouncer.tryAcquire(player1));
        assertTrue(debouncer.tryAcquire(player2));

        debouncer.clear();

        assertTrue(debouncer.tryAcquire(player1));
        assertTrue(debouncer.tryAcquire(player2));
    }

    @Test
    void validatesConstructorArguments() {
        assertThrows(NullPointerException.class, () -> new ClickDebouncer(null));
        assertThrows(IllegalArgumentException.class, () -> new ClickDebouncer(Duration.ofMillis(-1)));
    }
}
