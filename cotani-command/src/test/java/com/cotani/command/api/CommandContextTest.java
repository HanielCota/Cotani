package com.cotani.command.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.command.argument.Arguments;
import com.cotani.command.internal.DefaultCommandContext;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class CommandContextTest {

    @Test
    void shouldIdentifyPlayerAndConsoleSender() {
        var scheduler = mock(PaperTaskScheduler.class);
        var playerId = java.util.UUID.randomUUID();
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        var playerCtx = new DefaultCommandContext(player, Map.of(), List.of(), "test", scheduler);

        assertTrue(playerCtx.isPlayer());
        assertFalse(playerCtx.isConsole());
        assertEquals(player, playerCtx.requirePlayer());
        assertEquals(java.util.Optional.of(playerId), playerCtx.playerId());

        var console = mock(ConsoleCommandSender.class);
        var consoleCtx = new DefaultCommandContext(console, Map.of(), List.of(), "test", scheduler);

        assertFalse(consoleCtx.isPlayer());
        assertTrue(consoleCtx.isConsole());
        assertEquals(java.util.Optional.empty(), consoleCtx.playerId());
        assertThrows(CommandExecutionException.class, consoleCtx::requirePlayer);
    }

    @Test
    void shouldRetrieveParsedValuesAndOptionals() {
        var scheduler = mock(PaperTaskScheduler.class);
        var sender = mock(CommandSender.class);
        var argInt = Arguments.integer("amount");
        var argStr = Arguments.string("name");

        var ctx = new DefaultCommandContext(
                sender, Map.of("amount", 100, "name", "Haniel"), List.of("100", "Haniel"), "test", scheduler);

        assertEquals(100, ctx.get(argInt));
        assertEquals("Haniel", ctx.get(argStr));
        assertEquals(100, ctx.getInt("amount"));
        assertEquals("Haniel", ctx.getString("name"));
        assertEquals(100, ctx.get("amount", Integer.class));
        assertEquals("Haniel", ctx.get("name", String.class));
        assertTrue(ctx.has("amount"));
        assertTrue(ctx.has("name"));
        assertFalse(ctx.has("missing"));

        assertTrue(ctx.getOptional(argInt).isPresent());
        assertEquals(100, ctx.getOptional(argInt).get());
        assertTrue(ctx.getOptional("name", String.class).isPresent());
        assertTrue(ctx.getOptional("missing", String.class).isEmpty());
    }

    @Test
    void shouldRetrieveAllTypesOfTypedGetters() {
        var scheduler = mock(PaperTaskScheduler.class);
        var sender = mock(CommandSender.class);
        var player = mock(Player.class);
        var uuid = java.util.UUID.randomUUID();
        var duration = java.time.Duration.ofMinutes(10);
        var bigDecimal = new java.math.BigDecimal("99.99");

        var values = Map.<String, Object>of(
                "longVal",
                500L,
                "doubleVal",
                12.34,
                "bigDecVal",
                bigDecimal,
                "boolVal",
                true,
                "uuidVal",
                uuid,
                "playerVal",
                player,
                "refVal",
                new com.cotani.command.argument.PlayerRef(uuid, "Haniel"),
                "durationVal",
                duration);

        var ctx = new DefaultCommandContext(sender, values, List.of(), "test", scheduler);

        assertEquals(500L, ctx.getLong("longVal"));
        assertEquals(12.34, ctx.getDouble("doubleVal"));
        assertEquals(bigDecimal, ctx.getBigDecimal("bigDecVal"));
        assertTrue(ctx.getBoolean("boolVal"));
        assertEquals(uuid, ctx.getUUID("uuidVal"));
        assertEquals(player, ctx.get("playerVal", Player.class));
        assertEquals(duration, ctx.getDuration("durationVal"));

        var playerRef = ctx.getPlayerRef("refVal");
        assertEquals(uuid, playerRef.id());
        assertEquals("Haniel", playerRef.name());

        assertEquals(500L, ctx.getOptional("longVal", Long.class).orElseThrow());
        assertEquals(12.34, ctx.getOptional("doubleVal", Double.class).orElseThrow());
        assertEquals(
                bigDecimal,
                ctx.getOptional("bigDecVal", java.math.BigDecimal.class).orElseThrow());
        assertTrue(ctx.getOptional("boolVal", Boolean.class).orElseThrow());
        assertEquals(uuid, ctx.getOptional("uuidVal", java.util.UUID.class).orElseThrow());
        assertEquals(player, ctx.getOptional("playerVal", Player.class).orElseThrow());
        assertEquals(
                duration,
                ctx.getOptional("durationVal", java.time.Duration.class).orElseThrow());

        assertTrue(ctx.getOptional("missing", Long.class).isEmpty());
        assertTrue(ctx.getOptional("missing", Double.class).isEmpty());
        assertTrue(ctx.getOptional("missing", java.math.BigDecimal.class).isEmpty());
        assertTrue(ctx.getOptional("missing", Boolean.class).isEmpty());
        assertTrue(ctx.getOptional("missing", java.util.UUID.class).isEmpty());
        assertTrue(ctx.getOptional("missing", Player.class).isEmpty());
        assertTrue(ctx.getOptional("missing", java.time.Duration.class).isEmpty());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenMissingOrTypeMismatch() {
        var scheduler = mock(PaperTaskScheduler.class);
        var sender = mock(CommandSender.class);
        var ctx = new DefaultCommandContext(sender, Map.of("count", 10), List.of(), "test", scheduler);

        assertThrows(IllegalArgumentException.class, () -> ctx.get("missing", Integer.class));
        assertThrows(IllegalArgumentException.class, () -> ctx.get("count", String.class));
    }

    @Test
    void shouldSendReplyVariantsToSender() {
        var scheduler = mock(PaperTaskScheduler.class);
        var sender = mock(CommandSender.class);

        // Replies are delivered through the global scheduler for non-player senders; run inline.
        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .global(any(Runnable.class));

        var ctx = new DefaultCommandContext(sender, Map.of(), List.of(), "test", scheduler);

        ctx.reply("Hello world");
        ctx.replySuccess("Operation succeeded");
        ctx.replyInfo("Information note");
        ctx.replyError("Error occurred");

        ctx.reply("Hello <name>", com.cotani.text.Placeholders.unparsed("name", "User"));
        ctx.replySuccess("Success: <msg>", com.cotani.text.Placeholders.unparsed("msg", "Done"));
        ctx.replyInfo("Info: <msg>", com.cotani.text.Placeholders.unparsed("msg", "Note"));
        ctx.replyError("Error: <msg>", com.cotani.text.Placeholders.unparsed("msg", "Failed"));

        verify(sender, times(8)).sendMessage(any(Component.class));
    }
}
