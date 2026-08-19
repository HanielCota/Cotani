package com.cotani.cache.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cache.policy.CacheSettings;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
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
        var listener = PlayerDataCacheListener.create(cache, settings, logger);
        var event = new PlayerJoinEvent(player, (Component) null);

        listener.onJoin(event);

        verify(cache).loadAsync(playerId);
    }

    @Test
    void onQuitSavesAndUnloadsData() {
        var settings = CacheSettings.playerData();
        var listener = PlayerDataCacheListener.create(cache, settings, logger);
        var event = new PlayerQuitEvent(player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED);

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
        var listener = PlayerDataCacheListener.create(cache, settings, logger);
        var event = new PlayerQuitEvent(player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED);

        listener.onQuit(event);

        verify(cache).saveAsync(playerId);
        verify(cache).unload(playerId);
    }

    @Test
    void onJoinDoesNotLoadWhenDisabled() {
        var settings = CacheSettings.temporary();
        var listener = PlayerDataCacheListener.create(cache, settings, logger);
        var event = new PlayerJoinEvent(player, (Component) null);

        listener.onJoin(event);

        verify(cache, never()).loadAsync(any());
    }

    @Test
    void onQuitUnloadsWithoutSaveWhenSaveDisabled() {
        var settings =
                CacheSettings.builder().saveOnQuit(false).unloadOnQuit(true).build();
        var listener = PlayerDataCacheListener.create(cache, settings, logger);
        var event = new PlayerQuitEvent(player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED);

        listener.onQuit(event);

        verify(cache, never()).saveAsync(any());
        verify(cache).unload(playerId);
    }

    @Test
    void onQuitSavesWithoutUnloadWhenUnloadDisabled() {
        var settings =
                CacheSettings.builder().saveOnQuit(true).unloadOnQuit(false).build();
        var listener = PlayerDataCacheListener.create(cache, settings, logger);
        var event = new PlayerQuitEvent(player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED);

        listener.onQuit(event);

        verify(cache).saveAsync(playerId);
        verify(cache, never()).unload(any(UUID.class));
    }

    @Test
    void onQuitDoesNothingWhenBothDisabled() {
        var settings =
                CacheSettings.builder().saveOnQuit(false).unloadOnQuit(false).build();
        var listener = PlayerDataCacheListener.create(cache, settings, logger);
        var event = new PlayerQuitEvent(player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED);

        listener.onQuit(event);

        verify(cache, never()).saveAsync(any());
        verify(cache, never()).unload(any(UUID.class));
    }

    @Test
    void lateQuitSaveDoesNotUnloadNewerSession() {
        when(cache.loadAsync(playerId)).thenReturn(CompletableFuture.completedFuture("value"));
        var gate = new CompletableFuture<Void>();
        when(cache.saveAsync(playerId)).thenReturn(gate);

        var settings = CacheSettings.playerData();
        var listener = PlayerDataCacheListener.create(cache, settings, logger);

        listener.onJoin(new PlayerJoinEvent(player, (Component) null));
        listener.onQuit(new PlayerQuitEvent(player, (Component) null, PlayerQuitEvent.QuitReason.DISCONNECTED));
        listener.onJoin(new PlayerJoinEvent(player, (Component) null));
        gate.complete(null);

        verify(cache).saveAsync(playerId);
        verify(cache, never()).unload(playerId);
    }
}
