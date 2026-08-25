package com.cotani.dialog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.dialog.api.ChatPrompt;
import com.cotani.dialog.api.ConversationWizard;
import com.cotani.dialog.api.DialogService;
import com.cotani.dialog.api.PromptResult;
import com.cotani.dialog.internal.DefaultConversationWizard;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ConversationWizardTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldExecuteMultiStepWizardSuccessfully() {
        var dialogService = mock(DialogService.class);
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        var prompt1 = mock(ChatPrompt.class);
        var prompt2 = mock(ChatPrompt.class);

        when(prompt1.start(player)).thenReturn(CompletableFuture.completedFuture(PromptResult.success("Warrior")));
        when(prompt2.start(player)).thenReturn(CompletableFuture.completedFuture(PromptResult.success(10)));

        when(dialogService.createChatPrompt(any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(prompt1, prompt2);
        when(dialogService.createWizard(any()))
                .thenAnswer(inv -> new DefaultConversationWizard(inv.getArgument(0), dialogService));

        var wizard = ConversationWizard.builder()
                .step("name", ChatPrompt.of(Component.text("Step 1")))
                .step("level", ChatPrompt.builder().message("Step 2").parser(raw -> Optional.of(Integer.parseInt(raw))))
                .build(dialogService);

        var stage = wizard.start(player);
        var result = stage.toCompletableFuture().join();

        assertTrue(result.isSuccess());
        var map = result.valueOrThrow();
        assertEquals("Warrior", map.get("name"));
        assertEquals(10, map.get("level"));
    }
}
