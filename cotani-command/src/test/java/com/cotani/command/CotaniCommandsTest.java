package com.cotani.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.cotani.command.api.CommandBuilder;
import com.cotani.command.internal.BukkitCommandWrapper;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CotaniCommandsTest {

    private Plugin plugin;
    private Server server;
    private CommandMap commandMap;
    private PaperTaskScheduler scheduler;
    private Map<String, Command> knownCommands;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        server = mock(Server.class);
        commandMap = mock(CommandMap.class);
        scheduler = mock(PaperTaskScheduler.class);
        knownCommands = new HashMap<>();

        when(plugin.getName()).thenReturn("TestPlugin");
        when(plugin.getServer()).thenReturn(server);
        when(server.getCommandMap()).thenReturn(commandMap);
        when(commandMap.getKnownCommands()).thenReturn(knownCommands);
    }

    @Test
    void shouldRegisterCommandIntoCommandMap() {
        var module = CotaniCommands.create(plugin, scheduler);

        var cmd = CommandBuilder.of("ping").aliases("p").executes(ctx -> {}).build();

        module.register(cmd);

        verify(commandMap).register(eq("testplugin"), any(BukkitCommandWrapper.class));
    }

    @Test
    void shouldUnregisterCommandCleanly() {
        var module = CotaniCommands.create(plugin, scheduler);

        var cmd = CommandBuilder.of("ping").aliases("p").executes(ctx -> {}).build();

        module.register(cmd);
        module.unregister("ping");

        // Subsequent close does not fail
        assertDoesNotThrow(module::close);
    }

    @Test
    void shouldRegisterWithLambdaBuilder() {
        var module = CotaniCommands.create(plugin, scheduler);

        module.register("warp", root -> {
            root.description("Warp system").executes(ctx -> {});
        });

        verify(commandMap).register(eq("testplugin"), any(BukkitCommandWrapper.class));
    }

    @Test
    void shouldCreateWithCustomFeedback() {
        var feedback = com.cotani.command.feedback.CommandFeedback.ptBR();
        var module = CotaniCommands.create(plugin, scheduler, feedback);

        assertNotNull(module);
        var cmd = CommandBuilder.of("ajuda").executes(ctx -> {}).build();
        module.register(cmd);

        verify(commandMap).register(eq("testplugin"), any(BukkitCommandWrapper.class));
    }

    @Test
    void shouldUnregisterAllOnClose() {
        var module = CotaniCommands.create(plugin, scheduler);

        var cmd1 = CommandBuilder.of("cmd1").executes(ctx -> {}).build();
        var cmd2 = CommandBuilder.of("cmd2").executes(ctx -> {}).build();

        module.registerAll(cmd1, cmd2);
        module.close();

        // Closing twice is safe
        assertDoesNotThrow(module::close);
        assertDoesNotThrow(() -> module.closeAsync().toCompletableFuture().join());
    }
}
