package com.cotani.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.bukkit.Server;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Lifecycle tests for {@link CotaniGuiModule}: listener registration, debounce exposure and
 * idempotent close.
 */
final class CotaniGuiModuleTest {
    private final Plugin plugin = mock(Plugin.class);
    private final Server server = mock(Server.class);
    private final PluginManager pluginManager = mock(PluginManager.class);

    @BeforeEach
    void setUp() {
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
    }

    @Test
    void shouldRegisterGuardListener() {
        CotaniGuiModule.create(plugin);

        verify(pluginManager).registerEvents(any(Listener.class), eq(plugin));
    }

    @Test
    void shouldExposeDefaultDebounce() {
        var module = CotaniGuiModule.create(plugin);

        assertEquals(CotaniGuiModule.DEFAULT_DEBOUNCE, module.debounce());
    }

    @Test
    void shouldExposeConfiguredDebounce() {
        var module = CotaniGuiModule.create(plugin, Duration.ofMillis(42));

        assertEquals(Duration.ofMillis(42), module.debounce());
    }

    @Test
    void shouldUnregisterListenersAndClearStateOnClose() {
        var module = CotaniGuiModule.create(plugin);

        try (MockedStatic<HandlerList> handlerList = mockStatic(HandlerList.class)) {
            module.close();
            module.close();

            handlerList.verify(() -> HandlerList.unregisterAll(any(Listener.class)), times(1));
        }
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArguments() {
        assertThrows(NullPointerException.class, () -> CotaniGuiModule.create(null));
        assertThrows(NullPointerException.class, () -> CotaniGuiModule.create(plugin, null));
    }
}
