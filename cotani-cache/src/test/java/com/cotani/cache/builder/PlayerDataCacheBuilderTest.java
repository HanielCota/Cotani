package com.cotani.cache.builder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cache.exception.CacheException;
import com.cotani.cache.policy.CachePreset;
import com.cotani.cache.repository.CacheRepository;
import com.cotani.cache.repository.NoopCacheRepository;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class PlayerDataCacheBuilderTest {
    private final PaperTaskScheduler scheduler = mock(PaperTaskScheduler.class);

    @BeforeEach
    void setUp() {
        when(scheduler.asyncExecutor()).thenReturn(CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS));
        when(scheduler.asyncTimer(any(), any(), any())).thenReturn(SchedulerTask.noop());
    }

    private Plugin plugin() {
        var pluginManager = mock(PluginManager.class);
        var server = mock(Server.class);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getOnlinePlayers()).thenReturn(List.of());
        var plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("cache-test"));

        return plugin;
    }

    private PlayerDataCacheBuilder<String> builderWithDefaults() {
        return PlayerDataCacheBuilder.create(String.class).defaultValue(uniqueId -> "default");
    }

    @Test
    void buildWithoutDefaultValueThrows() {
        assertThrows(
                CacheException.class,
                () -> PlayerDataCacheBuilder.create(String.class).build(plugin(), scheduler));
    }

    @Test
    void buildReturnsPlayerDataCacheWithDefaults() {
        PlayerDataCache<String> cache = builderWithDefaults().build(plugin(), scheduler);

        assertNotNull(cache);
        assertTrue(cache.find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void buildWithoutRepositoryUsesNoop() {
        PlayerDataCache<String> cache = builderWithDefaults().build(plugin(), scheduler);

        assertNotNull(cache);
    }

    @Test
    void buildWithRepositoryAndPreset() {
        CacheRepository<UUID, String> repository = new NoopCacheRepository<>();
        PlayerDataCache<String> cache = builderWithDefaults()
                .repository(repository)
                .preset(CachePreset.PLAYER_DATA)
                .maximumConcurrentSaves(8)
                .build(plugin(), scheduler);

        assertNotNull(cache);
    }

    @Test
    void buildRejectsNullPlugin() {
        assertThrows(NullPointerException.class, () -> builderWithDefaults().build(null, scheduler));
    }

    @Test
    void buildRejectsNullScheduler() {
        assertThrows(NullPointerException.class, () -> builderWithDefaults().build(plugin(), null));
    }

    @Test
    void repositoryNullRejects() {
        assertThrows(NullPointerException.class, () -> builderWithDefaults().repository(null));
    }

    @Test
    void defaultValueNullRejects() {
        assertThrows(
                NullPointerException.class,
                () -> PlayerDataCacheBuilder.create(String.class).defaultValue(null));
    }

    @Test
    void presetNullRejects() {
        assertThrows(NullPointerException.class, () -> builderWithDefaults().preset(null));
    }

    @Test
    void invalidationBusNullRejects() {
        assertThrows(NullPointerException.class, () -> builderWithDefaults().invalidationBus(null));
    }

    @Test
    void maximumConcurrentSavesRequiresPositive() {
        var builder = builderWithDefaults();

        assertThrows(IllegalArgumentException.class, () -> builder.maximumConcurrentSaves(0));
        assertThrows(IllegalArgumentException.class, () -> builder.maximumConcurrentSaves(-1));
    }
}
