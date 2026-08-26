package com.cotani.command.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.cotani.command.api.CommandBuilder;
import com.cotani.command.argument.Arguments;
import com.cotani.command.feedback.CommandFeedback;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultCommandDispatcherTest {

    private Plugin plugin;
    private PaperTaskScheduler scheduler;
    private CommandFeedback feedback;
    private DefaultCommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("TestLogger"));
        scheduler = mock(PaperTaskScheduler.class);
        feedback = CommandFeedback.defaultFeedback();
        dispatcher = new DefaultCommandDispatcher(plugin, scheduler, feedback);
    }

    @Test
    void shouldDispatchSyncCommandWithArguments() {
        var executed = new AtomicBoolean(false);
        var parsedArg = new AtomicReference<String>();

        var cmd = CommandBuilder.of("greet")
                .argument(Arguments.string("name"))
                .executes(ctx -> {
                    executed.set(true);
                    parsedArg.set(ctx.get("name", String.class));
                })
                .build();

        var sender = mock(CommandSender.class);
        dispatcher.dispatch(cmd, sender, "greet", List.of("Haniel"));

        assertTrue(executed.get());
        assertEquals("Haniel", parsedArg.get());
    }

    @Test
    void shouldDispatchAsyncCommand() {
        var cmd = CommandBuilder.of("heavy").executesAsync(ctx -> null).build();

        var sender = mock(CommandSender.class);
        dispatcher.dispatch(cmd, sender, "heavy", List.of());

        verify(scheduler).async(eq("command-heavy"), any(Runnable.class));
    }

    @Test
    void shouldDispatchEntityCommandForPlayer() {
        var cmd = CommandBuilder.of("spawn").executesEntity((ctx, player) -> {}).build();

        var player = mock(Player.class);
        dispatcher.dispatch(cmd, player, "spawn", List.of());

        verify(scheduler).entity(eq(player), any(Runnable.class));
    }

    @Test
    void shouldDenyWhenPermissionIsMissing() {
        var executed = new AtomicBoolean(false);
        var cmd = CommandBuilder.of("admin")
                .permission("admin.permission")
                .executes(ctx -> executed.set(true))
                .build();

        var sender = mock(CommandSender.class);
        when(sender.hasPermission("admin.permission")).thenReturn(false);

        dispatcher.dispatch(cmd, sender, "admin", List.of());

        assertFalse(executed.get());
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    void shouldDenyConsoleWhenPlayerOnly() {
        var executed = new AtomicBoolean(false);
        var cmd = CommandBuilder.of("fly")
                .playerOnly()
                .executes(ctx -> executed.set(true))
                .build();

        var console = mock(ConsoleCommandSender.class);
        dispatcher.dispatch(cmd, console, "fly", List.of());

        assertFalse(executed.get());
        verify(console).sendMessage(any(Component.class));
    }

    @Test
    void shouldRouteToSubcommand() {
        var subExecuted = new AtomicBoolean(false);
        var subAmount = new AtomicReference<Integer>();

        var subCmd = CommandBuilder.of("deposit")
                .argument(Arguments.integer("amount"))
                .executes(ctx -> {
                    subExecuted.set(true);
                    subAmount.set(ctx.get("amount", Integer.class));
                })
                .build();

        var root = CommandBuilder.of("bank").subcommand(subCmd).build();

        var sender = mock(CommandSender.class);
        dispatcher.dispatch(root, sender, "bank", List.of("deposit", "100"));

        assertTrue(subExecuted.get());
        assertEquals(100, subAmount.get());
    }

    @Test
    void shouldEnforceCooldown() {
        var count = new AtomicInteger(0);
        var cmd = CommandBuilder.of("kit")
                .cooldown(Duration.ofMinutes(5))
                .executes(ctx -> count.incrementAndGet())
                .build();

        var sender = mock(CommandSender.class);

        dispatcher.dispatch(cmd, sender, "kit", List.of());
        assertEquals(1, count.get());

        dispatcher.dispatch(cmd, sender, "kit", List.of());
        assertEquals(1, count.get()); // Still 1 because cooldown active
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    void shouldProvideTabCompletionForSubcommandsAndArguments() {
        var subCmd = CommandBuilder.of("deposit")
                .argument(Arguments.choice("currency", "gold", "diamonds"))
                .build();

        var root = CommandBuilder.of("bank").subcommand(subCmd).build();

        var sender = mock(CommandSender.class);

        // Tab complete root subcommands starting with "dep"
        var rootSuggestions = dispatcher.complete(root, sender, "bank", List.of("dep"));
        assertEquals(List.of("deposit"), rootSuggestions);

        // Tab complete argument inside deposit
        var argSuggestions = dispatcher.complete(root, sender, "bank", List.of("deposit", "g"));
        assertEquals(List.of("gold"), argSuggestions);

        // Tab complete case-insensitively
        var upperSuggestions = dispatcher.complete(root, sender, "bank", List.of("DEP"));
        assertEquals(List.of("deposit"), upperSuggestions);
    }

    @Test
    void shouldHandleExecutionExceptionGracefully() {
        // Failure feedback is delivered through the global scheduler for non-player senders.
        doAnswer(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    task.run();
                    return null;
                })
                .when(scheduler)
                .global(any(Runnable.class));

        var cmd = CommandBuilder.of("crash")
                .executes(ctx -> {
                    throw new RuntimeException("Simulated failure");
                })
                .build();

        var sender = mock(CommandSender.class);
        assertDoesNotThrow(() -> dispatcher.dispatch(cmd, sender, "crash", List.of()));
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    void shouldSendUnknownSubcommandWhenInvalidSubcommandGiven() {
        var subCmd = CommandBuilder.of("buy").build();
        var root = CommandBuilder.of("shop").subcommand(subCmd).build();

        var sender = mock(CommandSender.class);
        dispatcher.dispatch(root, sender, "shop", List.of("invalidSub"));

        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    void shouldHandleOptionalArgumentWithDefaultValueWhenOmitted() {
        var capturedAmount = new AtomicInteger(0);
        var cmd = CommandBuilder.of("give")
                .argument(Arguments.string("item"))
                .argument(Arguments.integer("amount").withDefault(64))
                .executes(ctx -> capturedAmount.set(ctx.getInt("amount")))
                .build();

        var sender = mock(CommandSender.class);
        dispatcher.dispatch(cmd, sender, "give", List.of("diamond"));

        assertEquals(64, capturedAmount.get());
    }

    @Test
    void shouldRouteThroughNestedSubcommandChain() {
        var executed = new AtomicBoolean(false);
        var permissionTarget = new AtomicReference<String>();

        var root = CommandBuilder.of("admin")
                .subcommand(
                        "user",
                        user -> user.subcommand(
                                "permission",
                                perm -> perm.subcommand("add", add -> {
                                    add.argument(Arguments.string("permissionNode"))
                                            .executes(ctx -> {
                                                executed.set(true);
                                                permissionTarget.set(ctx.getString("permissionNode"));
                                            });
                                })))
                .build();

        var sender = mock(CommandSender.class);
        dispatcher.dispatch(root, sender, "admin", List.of("user", "permission", "add", "cotani.admin.fly"));

        assertTrue(executed.get());
        assertEquals("cotani.admin.fly", permissionTarget.get());
    }

    @Test
    void shouldBlockPlayerWhenCommandIsConsoleOnly() {
        var executed = new AtomicBoolean(false);
        var cmd = CommandBuilder.of("stop")
                .consoleOnly()
                .executes(ctx -> executed.set(true))
                .build();

        var player = mock(Player.class);
        dispatcher.dispatch(cmd, player, "stop", List.of());

        assertFalse(executed.get());
        verify(player).sendMessage(any(Component.class));
    }

    @Test
    void shouldFailWhenRequiredArgumentIsMissing() {
        var executed = new AtomicBoolean(false);
        var cmd = CommandBuilder.of("teleport")
                .argument(Arguments.string("target"))
                .executes(ctx -> executed.set(true))
                .build();

        var sender = mock(CommandSender.class);
        dispatcher.dispatch(cmd, sender, "teleport", List.of());

        assertFalse(executed.get());
        verify(sender).sendMessage(any(Component.class));
    }
}
