package com.cotani.command.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.UUID;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class CooldownEvaluatorTest {

    @Test
    void shouldNeverBlockWhenCooldownIsNone() {
        var evaluator = CooldownEvaluator.none();
        var player = mock(Player.class);

        assertTrue(evaluator.check(player, "ping").isEmpty());

        evaluator.apply(player, "ping");
        assertTrue(evaluator.check(player, "ping").isEmpty());
    }

    @Test
    void shouldEnforceInMemoryDurationCooldown() {
        var evaluator = CooldownEvaluator.of(Duration.ofSeconds(10));
        var player1 = mock(Player.class);
        var uuid1 = UUID.randomUUID();
        when(player1.getUniqueId()).thenReturn(uuid1);

        var player2 = mock(Player.class);
        var uuid2 = UUID.randomUUID();
        when(player2.getUniqueId()).thenReturn(uuid2);

        assertTrue(evaluator.check(player1, "daily").isEmpty());
        assertTrue(evaluator.check(player2, "daily").isEmpty());

        evaluator.apply(player1, "daily");

        assertTrue(evaluator.check(player1, "daily").isPresent());
        var remaining = evaluator.check(player1, "daily").get();
        assertFalse(remaining.isZero());
        assertTrue(remaining.toSeconds() <= 10);

        // Player 2 is independent and still ready
        assertTrue(evaluator.check(player2, "daily").isEmpty());
    }

    @Test
    void shouldEvaluateCommandNameCaseInsensitively() {
        var evaluator = CooldownEvaluator.of(Duration.ofSeconds(5));
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        assertTrue(evaluator.check(player, "WARP").isEmpty());
        evaluator.apply(player, "warp");

        assertTrue(evaluator.check(player, "WARP").isPresent());
        assertTrue(evaluator.check(player, "Warp").isPresent());
        assertTrue(evaluator.check(player, "warp").isPresent());
    }

    @Test
    void shouldSupportConsoleSenderCooldown() {
        var evaluator = CooldownEvaluator.of(Duration.ofSeconds(5));
        var console = mock(ConsoleCommandSender.class);

        assertTrue(evaluator.check(console, "broadcast").isEmpty());
        evaluator.apply(console, "broadcast");

        assertTrue(evaluator.check(console, "broadcast").isPresent());
    }
}
