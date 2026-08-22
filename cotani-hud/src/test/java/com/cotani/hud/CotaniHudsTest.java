package com.cotani.hud;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.task.api.PaperTaskScheduler;
import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CotaniHudsTest {

    private Plugin plugin;
    private Server server;
    private PluginManager pluginManager;
    private PaperTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        scheduler = mock(PaperTaskScheduler.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
    }

    @Test
    void shouldCreateHudModuleAndRegisterListener() {
        var hud = CotaniHuds.create(plugin, scheduler);

        assertNotNull(hud);
        assertNotNull(hud.sidebar());
        assertNotNull(hud.tabList());
        assertNotNull(hud.bossBar());
        assertNotNull(hud.actionBar());

        verify(pluginManager).registerEvents(any(Listener.class), eq(plugin));
    }

    @Test
    void shouldCloseCleanly() {
        var hud = CotaniHuds.create(plugin, scheduler);

        assertDoesNotThrow(hud::close);
        assertDoesNotThrow(hud::close);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArguments() {
        assertThrows(NullPointerException.class, () -> CotaniHuds.create(null, scheduler));
        assertThrows(NullPointerException.class, () -> CotaniHuds.create(plugin, null));
    }
}
