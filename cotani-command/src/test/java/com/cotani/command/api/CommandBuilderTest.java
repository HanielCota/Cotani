package com.cotani.command.api;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.command.argument.Arguments;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommandBuilderTest {

    @Test
    void shouldBuildBasicCommand() {
        var node = CommandBuilder.of("test")
                .aliases("t", "testcmd")
                .description("Test command description")
                .usage("/test <arg>")
                .permission("test.permission")
                .playerOnly()
                .cooldown(Duration.ofSeconds(5))
                .argument(Arguments.string("param"))
                .executes(ctx -> {})
                .build();

        assertEquals("test", node.name());
        assertEquals(Set.of("t", "testcmd"), node.aliases());
        assertTrue(node.description().isPresent());
        assertEquals("Test command description", node.description().get());
        assertTrue(node.usage().isPresent());
        assertEquals("/test <arg>", node.usage().get());
        assertEquals(SenderType.PLAYER, node.senderType());
        assertEquals(CommandExecutionMode.SYNC, node.executionMode());
        assertEquals(1, node.arguments().size());
        assertTrue(node.canExecute());
    }

    @Test
    void shouldBuildSubcommands() {
        var subNode = CommandBuilder.of("add")
                .argument(Arguments.integer("amount"))
                .executes(ctx -> {})
                .build();

        var root = CommandBuilder.of("points").subcommand(subNode).build();

        assertEquals("points", root.name());
        assertTrue(root.hasSubcommands());
        assertTrue(root.findSubcommand("add").isPresent());
        assertEquals("add", root.findSubcommand("add").get().name());
    }

    @Test
    void shouldSetAsyncAndEntityTargets() {
        var asyncNode = CommandBuilder.of("async").executesAsync(ctx -> null).build();
        assertEquals(CommandExecutionMode.ASYNC, asyncNode.executionMode());

        var entityNode =
                CommandBuilder.of("entity").executesEntity((ctx, player) -> {}).build();
        assertEquals(CommandExecutionMode.ENTITY_REGION, entityNode.executionMode());
        assertEquals(SenderType.PLAYER, entityNode.senderType());
    }

    @Test
    void shouldBuildSubcommandWithLambda() {
        var root = CommandBuilder.of("wallet")
                .subcommand("balance", sub -> sub.playerOnly().executes(ctx -> {}))
                .build();

        assertTrue(root.hasSubcommands());
        assertTrue(root.findSubcommand("balance").isPresent());
        assertEquals(SenderType.PLAYER, root.findSubcommand("balance").get().senderType());
    }

    @Test
    void shouldBuildCommandWithCooldownService() {
        var cooldownService = org.mockito.Mockito.mock(com.cotani.cooldown.api.CooldownService.class);
        var node = CommandBuilder.of("pay")
                .cooldown(cooldownService, Duration.ofSeconds(3))
                .executes(ctx -> {})
                .build();

        assertNotNull(node.cooldown());
    }
}
