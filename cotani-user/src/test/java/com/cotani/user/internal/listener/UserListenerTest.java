package com.cotani.user.internal.listener;

import static org.mockito.Mockito.*;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.user.internal.service.InternalUserService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

@SuppressWarnings({"NullAway", "removal"})
class UserListenerTest {

    private final Plugin plugin = mock(Plugin.class);
    private final InternalUserService userService = mock(InternalUserService.class);
    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);
    private final Component failureMessage = Component.text("fail");
    private final UserListener listener = new UserListener(plugin, userService, scheduler, failureMessage);

    {
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger(UserListenerTest.class.getName()));
    }

    @Test
    void onJoinStartsLoad() {
        Player player = mock(Player.class);
        UUID uniqueId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uniqueId);
        when(player.getName()).thenReturn("Steve");
        when(userService.load(uniqueId, "Steve")).thenReturn(CompletableFuture.completedFuture(null));

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        verify(userService).load(uniqueId, "Steve");
    }

    @Test
    void onJoinKicksPlayerWhenLoadFails() {
        Player player = mock(Player.class);
        UUID uniqueId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uniqueId);
        when(player.getName()).thenReturn("Steve");
        when(player.isOnline()).thenReturn(true);
        when(userService.load(uniqueId, "Steve"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        ArgumentCaptor<Runnable> global = ArgumentCaptor.forClass(Runnable.class);
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));
        verify(scheduler).global(anyString(), global.capture());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(uniqueId)).thenReturn(player);
            global.getValue().run();
        }

        verify(player).kick(failureMessage);
    }

    @Test
    void onJoinUnloadsPlayerWhenGoesOfflineBeforeLoadCompletes() {
        Player player = mock(Player.class);
        UUID uniqueId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uniqueId);
        when(player.getName()).thenReturn("Steve");
        when(userService.load(uniqueId, "Steve")).thenReturn(CompletableFuture.completedFuture(null));
        when(userService.unload(uniqueId)).thenReturn(CompletableFuture.completedFuture(null));

        ArgumentCaptor<Runnable> global = ArgumentCaptor.forClass(Runnable.class);
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));
        verify(scheduler).global(anyString(), global.capture());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(uniqueId)).thenReturn(null);
            global.getValue().run();
        }

        verify(userService).unload(uniqueId);
    }

    @Test
    void onQuitUnloadsUser() {
        Player player = mock(Player.class);
        UUID uniqueId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uniqueId);
        when(userService.unload(uniqueId)).thenReturn(CompletableFuture.completedFuture(null));

        listener.onQuit(new PlayerQuitEvent(player, Component.empty()));

        verify(userService).unload(uniqueId);
    }

    @Test
    void onJoinUsesImmediateSnapshotValues() {
        Player player = mock(Player.class);
        UUID uniqueId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uniqueId);
        when(player.getName()).thenReturn("Steve");
        when(userService.load(uniqueId, "Steve")).thenReturn(CompletableFuture.completedFuture(null));

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        verify(player, atLeastOnce()).getUniqueId();
        verify(player, atLeastOnce()).getName();
    }
}
