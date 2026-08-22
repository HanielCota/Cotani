package com.cotani.nametag;

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

class CotaniNametagsTest {

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
    void shouldCreateNametagModuleAndRegisterListener() {
        var module = CotaniNametags.create(plugin, scheduler);

        assertNotNull(module);
        verify(pluginManager).registerEvents(any(Listener.class), eq(plugin));
    }

    @Test
    void shouldCloseCleanly() {
        var module = CotaniNametags.create(plugin, scheduler);

        assertDoesNotThrow(module::close);
        assertDoesNotThrow(module::close);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArguments() {
        assertThrows(NullPointerException.class, () -> CotaniNametags.create(null, scheduler));
        assertThrows(NullPointerException.class, () -> CotaniNametags.create(plugin, null));
    }
}
