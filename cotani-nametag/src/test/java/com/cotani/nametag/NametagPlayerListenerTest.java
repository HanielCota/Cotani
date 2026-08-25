package com.cotani.nametag;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.nametag.internal.DefaultNametagModule;
import com.cotani.nametag.internal.NametagPlayerListener;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NametagPlayerListenerTest {

    private DefaultNametagModule module;
    private NametagPlayerListener listener;
    private Player player;

    @BeforeEach
    void setUp() {
        module = mock(DefaultNametagModule.class);
        listener = new NametagPlayerListener(module);
        player = mock(Player.class);
    }

    @Test
    void shouldDelegateJoinEvent() {
        var event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerJoin(event);
        verify(module).handlePlayerJoin(player);
    }

    @Test
    void shouldDelegateQuitEvent() {
        var event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerQuit(event);
        verify(module).handlePlayerQuit(player);
    }

    @Test
    void shouldDelegateWorldChangeEvent() {
        var event = mock(PlayerChangedWorldEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerChangedWorld(event);
        verify(module).handlePlayerRefresh(player);
    }

    @Test
    void shouldDelegateRespawnEvent() {
        var event = mock(PlayerRespawnEvent.class);
        when(event.getPlayer()).thenReturn(player);

        listener.onPlayerRespawn(event);
        verify(module).handlePlayerRefresh(player);
    }
}
