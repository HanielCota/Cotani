package com.cotani.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

import com.cotani.cache.api.DataCache;
import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cache.policy.CachePreset;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("NullAway")
class CotaniCacheBehaviorTest {
    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);

    @Mock
    private CacheRepository<String, String> repository;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        when(scheduler.asyncExecutor()).thenReturn(CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS));
        when(scheduler.asyncTimer(any(), any(), any())).thenReturn(SchedulerTask.noop());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    private Plugin plugin() {
        var pluginManager = mock(PluginManager.class);
        var server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getOnlinePlayers()).thenReturn(List.of());
        var plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("cache-behavior-test"));

        return plugin;
    }

    private Plugin pluginWithPlayers(UUID... playerIds) {
        var playerList = Arrays.stream(playerIds)
                .map(playerId -> {
                    var player = mock(Player.class);
                    when(player.getUniqueId()).thenReturn(playerId);

                    return player;
                })
                .toList();
        var pluginManager = mock(PluginManager.class);
        var server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        doReturn(playerList).when(server).getOnlinePlayers();
        var plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("cache-behavior-test"));

        return plugin;
    }

    @Test
    void dataBuilderFunctionDefaultValueReceivesKey() throws Exception {
        when(repository.find(anyString())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(key -> "default:" + key)
                .repository(repository)
                .build(scheduler);

        assertEquals(
                "default:key-1", cache.getOrLoad("key-1").toCompletableFuture().get(5, TimeUnit.SECONDS));
    }

    @Test
    void dataBuilderWithoutRepositoryUsesNoopRepository() throws Exception {
        DataCache<String, String> cache = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .build(scheduler);

        assertEquals("default", cache.getOrLoad("key").toCompletableFuture().get(5, TimeUnit.SECONDS));
    }

    @Test
    void temporaryFactoryAppliesExpireAfterWriteAndDefaults() throws Exception {
        DataCache<String, String> cache = CotaniCache.temporary(String.class, String.class, Duration.ofMinutes(1))
                .defaultValue(() -> "default")
                .build(scheduler);

        assertEquals("default", cache.getOrLoad("key").toCompletableFuture().get(5, TimeUnit.SECONDS));
    }

    @Test
    void playersBuilderDefaultValueFactoryReceivesPlayerUuid() throws Exception {
        @SuppressWarnings("unchecked")
        CacheRepository<UUID, String> uuidRepository = mock(CacheRepository.class);
        when(uuidRepository.find(any(UUID.class))).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        var captured = new AtomicReference<UUID>();
        PlayerDataCache<String> cache = CotaniCache.players(String.class)
                .defaultValue(uniqueId -> {
                    captured.set(uniqueId);

                    return "value-for-player";
                })
                .repository(uuidRepository)
                .build(plugin(), scheduler);
        UUID id = UUID.randomUUID();

        assertEquals(
                "value-for-player",
                cache.getOrLoadAsync(id).toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(id, captured.get());
    }

    @Test
    void playersBuilderLoadsOnlinePlayersOnBuildWhenLoadOnJoin() throws Exception {
        @SuppressWarnings("unchecked")
        CacheRepository<UUID, String> uuidRepository = mock(CacheRepository.class);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var loaded = new CountDownLatch(2);
        when(uuidRepository.find(any(UUID.class))).thenAnswer(_ -> {
            loaded.countDown();

            return CompletableFuture.completedFuture(Optional.empty());
        });

        CotaniCache.players(String.class)
                .defaultValue(uniqueId -> "default")
                .repository(uuidRepository)
                .build(pluginWithPlayers(first, second), scheduler);

        assertTrue(loaded.await(5, TimeUnit.SECONDS));
        verify(uuidRepository).find(first);
        verify(uuidRepository).find(second);
    }

    @Test
    void playersBuilderSkipsOnlineLoadWhenLoadOnJoinDisabled() {
        @SuppressWarnings("unchecked")
        CacheRepository<UUID, String> uuidRepository = mock(CacheRepository.class);

        PlayerDataCache<String> cache = CotaniCache.players(String.class)
                .defaultValue(uniqueId -> "default")
                .preset(CachePreset.TEMPORARY)
                .repository(uuidRepository)
                .build(pluginWithPlayers(UUID.randomUUID()), scheduler);

        assertNotNull(cache);
        verifyNoInteractions(uuidRepository);
    }

    @Test
    void dataBuilderWithNegativeMaximumSizeFailsFast() {
        var builder = CotaniCache.data(String.class, String.class)
                .defaultValue(() -> "default")
                .maximumSize(-1);

        assertThrows(IllegalArgumentException.class, () -> builder.build(scheduler));
    }
}
