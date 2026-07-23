package com.cotani.cache.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cache.policy.CacheSettings;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("NullAway")
class PlayerDataCacheListenerTest {

    @Mock
    private PlayerDataCache<String> cache;

    @Mock
    private Player player;

    private final Logger logger = Logger.getLogger(PlayerDataCacheListenerTest.class.getName());
    private final UUID playerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(player.getUniqueId()).thenReturn(playerId);
        when(cache.saveAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void onJoinLoadsDataWhenEnabled() {
        var settings = CacheSettings.playerData();
        var listener = new PlayerDataCacheListener<>(cache, settings, logger);
        var event = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null);

        listener.onJoin(event);

        verify(cache).loadAsync(playerId);
    }

    @Test
    void onQuitSavesAndUnloadsData() {
        var settings = CacheSettings.playerData();
        var listener = new PlayerDataCacheListener<>(cache, settings, logger);
        var event = new PlayerQuitEvent(
                player, (net.kyori.adventure.text.Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED);

        listener.onQuit(event);

        verify(cache).saveAsync(playerId);
        verify(cache).unload(playerId);
    }

    @Test
    void onQuitUnloadsDataEvenWhenSaveFails() {
        var failedFuture = new CompletableFuture<Void>();
        failedFuture.completeExceptionally(new RuntimeException("Save error"));
        when(cache.saveAsync(playerId)).thenReturn(failedFuture);

        var settings = CacheSettings.playerData();
        var listener = new PlayerDataCacheListener<>(cache, settings, logger);
        var event = new PlayerQuitEvent(
                player, (net.kyori.adventure.text.Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED);

        listener.onQuit(event);

        verify(cache).saveAsync(playerId);
        verify(cache).unload(playerId);
    }
}
