package com.cotani.dialog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.dialog.api.CancelReason;
import com.cotani.dialog.internal.ActivePrompt;
import com.cotani.dialog.internal.DefaultDialogService;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultDialogServiceTest {

    private Plugin plugin;
    private PaperTaskScheduler scheduler;
    private DefaultDialogService dialogService;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        var server = mock(Server.class);
        var pm = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pm);
        scheduler = mock(PaperTaskScheduler.class);

        dialogService = new DefaultDialogService(plugin, scheduler);
    }

    @Test
    void shouldRegisterAndCancelActivePrompt() {
        var playerId = UUID.randomUUID();
        var prompt = mock(ActivePrompt.class);
        when(prompt.playerId()).thenReturn(playerId);

        dialogService.registerActivePrompt(prompt);
        assertTrue(dialogService.hasActivePrompt(playerId));
        assertEquals(1, dialogService.activePromptsCount());

        boolean cancelled = dialogService.cancelPrompt(playerId, CancelReason.USER_CANCELLED);
        assertTrue(cancelled);
        verify(prompt).cancel(CancelReason.USER_CANCELLED);
        assertFalse(dialogService.hasActivePrompt(playerId));
        assertEquals(0, dialogService.activePromptsCount());
    }

    @Test
    void shouldCancelPreviousPromptWhenOverridden() {
        var playerId = UUID.randomUUID();
        var prompt1 = mock(ActivePrompt.class);
        var prompt2 = mock(ActivePrompt.class);
        when(prompt1.playerId()).thenReturn(playerId);
        when(prompt2.playerId()).thenReturn(playerId);

        dialogService.registerActivePrompt(prompt1);
        dialogService.registerActivePrompt(prompt2);

        verify(prompt1).cancel(CancelReason.OVERRIDDEN);
        assertEquals(1, dialogService.activePromptsCount());
        assertSame(prompt2, dialogService.getActivePrompt(playerId));
    }

    @Test
    void shouldCancelAllOnClose() {
        var p1 = mock(ActivePrompt.class);
        var p2 = mock(ActivePrompt.class);
        when(p1.playerId()).thenReturn(UUID.randomUUID());
        when(p2.playerId()).thenReturn(UUID.randomUUID());

        dialogService.registerActivePrompt(p1);
        dialogService.registerActivePrompt(p2);

        dialogService.close();

        verify(p1).cancel(CancelReason.PLUGIN_DISABLE);
        verify(p2).cancel(CancelReason.PLUGIN_DISABLE);
        assertEquals(0, dialogService.activePromptsCount());
    }
}
