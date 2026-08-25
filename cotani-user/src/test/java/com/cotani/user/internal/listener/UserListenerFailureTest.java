package com.cotani.user.internal.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.user.internal.service.InternalUserService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@SuppressWarnings("NullAway")
class UserListenerFailureTest {
    private final Plugin plugin = mock(Plugin.class);
    private final Logger logger = mock(Logger.class);
    private final InternalUserService userService = mock(InternalUserService.class);
    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
    private final Component failureMessage = Component.text("fail");
    private final UserListener listener = new UserListener(plugin, userService, scheduler, failureMessage);

    {
        when(plugin.getLogger()).thenReturn(logger);
    }

    @Test
    void onJoinLogsLoadFailure() {
        Player player = mock(Player.class);
        UUID uniqueId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uniqueId);
        when(player.getName()).thenReturn("Steve");
        RuntimeException failure = new RuntimeException("boom");
        when(userService.load(uniqueId, "Steve")).thenReturn(CompletableFuture.failedFuture(failure));

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        verify(logger).log(eq(Level.SEVERE), eq(failure), any());
    }

    @Test
    void onJoinDoesNotKickOfflinePlayerWhenLoadFails() {
        Player player = mock(Player.class);
        UUID uniqueId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uniqueId);
        when(player.getName()).thenReturn("Steve");
        when(userService.load(uniqueId, "Steve"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        ArgumentCaptor<Runnable> entityTask = ArgumentCaptor.forClass(Runnable.class);
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));
        verify(scheduler).entity(anyString(), eq(uniqueId), entityTask.capture());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(uniqueId)).thenReturn(null);
            entityTask.getValue().run();
        }

        verify(player, never()).kick(any());
    }

    @Test
    void onJoinLogsWhenBukkitThrowsInsideGlobalTask() {
        Player player = mock(Player.class);
        UUID uniqueId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uniqueId);
        when(player.getName()).thenReturn("Steve");
        when(userService.load(uniqueId, "Steve"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        ArgumentCaptor<Runnable> entityTask = ArgumentCaptor.forClass(Runnable.class);
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));
        verify(scheduler).entity(anyString(), eq(uniqueId), entityTask.capture());

        RuntimeException bukkitFailure = new IllegalStateException("server down");
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(uniqueId)).thenThrow(bukkitFailure);
            entityTask.getValue().run();
        }

        verify(logger).log(eq(Level.SEVERE), eq(bukkitFailure), any());
        verify(player, never()).kick(any());
    }

    @Test
    void onQuitLogsUnloadFailure() {
        Player player = mock(Player.class);
        UUID uniqueId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uniqueId);
        RuntimeException failure = new RuntimeException("boom");
        when(userService.unload(uniqueId)).thenReturn(CompletableFuture.failedFuture(failure));

        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        verify(logger).log(eq(Level.SEVERE), any(Throwable.class), any());
    }
}
