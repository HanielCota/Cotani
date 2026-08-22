package com.cotani.command.feedback;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.text.MiniMessages;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommandFeedbackTest {

    @Test
    void shouldFormatDefaultMessages() {
        var feedback = CommandFeedback.defaultFeedback();

        assertNotNull(feedback.formatUnknownCommand());
        assertNotNull(feedback.formatUnknownSubcommand("foo", "/bar"));
        assertNotNull(feedback.formatInvalidUsage("/foo <bar>"));
        assertNotNull(feedback.formatPermissionDenied(Optional.of("test.node")));
        assertNotNull(feedback.formatPlayerOnly());
        assertNotNull(feedback.formatConsoleOnly());
        assertNotNull(feedback.formatCooldownActive(Duration.ofSeconds(15)));
        assertNotNull(feedback.formatExecutionError());
    }

    @Test
    void shouldFormatCustomMessages() {
        var feedback = CommandFeedback.builder()
                .playerOnly("<red>Somente jogadores podem usar!</red>")
                .cooldownActive("<yellow>Aguarde <remaining>!</yellow>")
                .build();

        var playerOnly = feedback.formatPlayerOnly();
        assertEquals("Somente jogadores podem usar!", MiniMessages.strip(MiniMessages.serialize(playerOnly)));

        var cooldown = feedback.formatCooldownActive(Duration.ofSeconds(30));
        assertTrue(MiniMessages.serialize(cooldown).contains("30s"));
    }

    @Test
    void shouldFormatPortugueseMessages() {
        var feedback = CommandFeedback.ptBR();
        assertNotNull(feedback.formatUnknownCommand());
        assertNotNull(feedback.formatUnknownSubcommand("ajuda", "/menu"));
        assertNotNull(feedback.formatInvalidUsage("/warp <nome>"));
        assertNotNull(feedback.formatPermissionDenied(Optional.of("admin.teleport")));
        assertNotNull(feedback.formatPermissionDenied(Optional.empty()));
        assertNotNull(feedback.formatPlayerOnly());
        assertNotNull(feedback.formatConsoleOnly());
        assertNotNull(feedback.formatCooldownActive(Duration.ofSeconds(60)));
        assertNotNull(feedback.formatExecutionError());

        var stripped = MiniMessages.strip(MiniMessages.serialize(feedback.formatPlayerOnly()));
        assertTrue(stripped.contains("jogadores"));
    }

    @Test
    void shouldCustomizeAllBuilderTemplates() {
        var feedback = CommandFeedback.builder()
                .unknownCommand("<red>Custom Unknown</red>")
                .unknownSubcommand("<red>Unknown Sub: <input> <usage></red>")
                .invalidUsage("<red>Usage: <usage></red>")
                .permissionDenied("<red>Denied: <permission></red>")
                .playerOnly("<red>Players</red>")
                .consoleOnly("<red>Console</red>")
                .cooldownActive("<yellow>Wait: <remaining></yellow>")
                .executionError("<red>Failed</red>")
                .build();

        assertEquals("Custom Unknown", MiniMessages.strip(MiniMessages.serialize(feedback.formatUnknownCommand())));
        assertTrue(MiniMessages.strip(MiniMessages.serialize(feedback.formatUnknownSubcommand("sub", "/root")))
                .contains("sub"));
        assertTrue(MiniMessages.strip(MiniMessages.serialize(feedback.formatInvalidUsage("/cmd")))
                .contains("/cmd"));
        assertTrue(MiniMessages.strip(MiniMessages.serialize(feedback.formatPermissionDenied(Optional.of("node.test"))))
                .contains("node.test"));
        assertEquals("Players", MiniMessages.strip(MiniMessages.serialize(feedback.formatPlayerOnly())));
        assertEquals("Console", MiniMessages.strip(MiniMessages.serialize(feedback.formatConsoleOnly())));
        assertTrue(MiniMessages.strip(MiniMessages.serialize(feedback.formatCooldownActive(Duration.ofSeconds(5))))
                .contains("5s"));
        assertEquals("Failed", MiniMessages.strip(MiniMessages.serialize(feedback.formatExecutionError())));
    }
}
