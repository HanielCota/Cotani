package com.cotani.cache.builder;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cache.policy.CachePreset;
import com.cotani.cache.policy.CacheSettings;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class PlayerDataCacheBuilderBehaviorTest {
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
        when(plugin.getLogger()).thenReturn(Logger.getLogger("cache-builder-behavior-test"));

        return plugin;
    }

    private PlayerDataCacheBuilder<String> builderWithDefaults() {
        return PlayerDataCacheBuilder.create(String.class).defaultValue(uniqueId -> "default");
    }

    @Test
    void settingsNullRejects() {
        assertThrows(NullPointerException.class, () -> builderWithDefaults().settings(null));
    }

    @Test
    void presetThenCustomSettingsBuildsSuccessfully() {
        CacheSettings custom = CacheSettings.builder()
                .maximumSize(100)
                .expireAfterAccess(Duration.ofMinutes(5))
                .build();

        PlayerDataCache<String> cache = builderWithDefaults()
                .preset(CachePreset.PLAYER_DATA)
                .settings(custom)
                .build(plugin(), scheduler);

        assertNotNull(cache);
    }
}
