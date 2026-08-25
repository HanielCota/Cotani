package com.cotani.dialog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.dialog.api.CancelReason;
import com.cotani.dialog.internal.DefaultChatPrompt;
import com.cotani.dialog.internal.DefaultDialogService;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatPromptTest {

    private DefaultDialogService dialogService;
    private PaperTaskScheduler scheduler;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        dialogService = mock(DefaultDialogService.class);
        scheduler = mock(PaperTaskScheduler.class);
        player = mock(Player.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
    }

    @Test
    void shouldParseValidIntegerInput() {
        DefaultChatPrompt<Integer> prompt = new DefaultChatPrompt<>(
                Component.text("Enter amount:"),
                Duration.ofSeconds(10),
                Set.of("cancel"),
                3,
                raw -> {
                    try {
                        return Optional.of(Integer.parseInt(raw));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                },
                (_, input) -> Component.text("Invalid: " + input),
                null,
                dialogService,
                scheduler);

        var stage = prompt.start(player);
        prompt.handleInput(player, "42");

        var result = stage.toCompletableFuture().join();
        assertTrue(result.isSuccess());
        assertEquals(42, result.valueOrThrow());
    }

    @Test
    void shouldCancelOnCancelKeyword() {
        DefaultChatPrompt<String> prompt = new DefaultChatPrompt<>(
                Component.text("Enter name:"),
                Duration.ofSeconds(10),
                Set.of("cancel", "sair"),
                3,
                Optional::of,
                (_, input) -> Component.text("Invalid"),
                null,
                dialogService,
                scheduler);

        var stage = prompt.start(player);
        prompt.handleInput(player, "cancel");

        var result = stage.toCompletableFuture().join();
        assertTrue(result.isCancelled());
        assertEquals(
                CancelReason.USER_CANCELLED, ((com.cotani.dialog.api.PromptResult.Cancelled<String>) result).reason());
    }

    @Test
    void shouldTimeoutAfterExceedingMaxAttempts() {
        DefaultChatPrompt<Integer> prompt = new DefaultChatPrompt<>(
                Component.text("Enter integer:"),
                Duration.ofSeconds(10),
                Set.of("cancel"),
                2,
                raw -> {
                    try {
                        return Optional.of(Integer.parseInt(raw));
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                },
                (_, _) -> Component.text("Not a number"),
                null,
                dialogService,
                scheduler);

        var stage = prompt.start(player);
        prompt.handleInput(player, "abc"); // Attempt 1 (fails)
        verify(player).sendMessage(Component.text("Not a number"));

        prompt.handleInput(player, "def"); // Attempt 2 (fails)
        prompt.handleInput(player, "ghi"); // Attempt 3 (exceeds maxAttempts 2 -> cancelled)

        var result = stage.toCompletableFuture().join();
        assertTrue(result.isCancelled());
        assertEquals(
                CancelReason.MAX_ATTEMPTS_EXCEEDED,
                ((com.cotani.dialog.api.PromptResult.Cancelled<Integer>) result).reason());
    }
}
