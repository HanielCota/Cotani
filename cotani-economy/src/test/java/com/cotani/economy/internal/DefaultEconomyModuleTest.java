package com.cotani.economy.internal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.Cotani;
import com.cotani.economy.CotaniEconomy;
import com.cotani.economy.api.EconomyModule;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.task.api.PaperTaskScheduler;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class DefaultEconomyModuleTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateModuleWiringServiceAndConfiguration() {
        var plugin = newPlugin();
        var storage = mock(CotaniStorage.class);
        var scheduler = mock(PaperTaskScheduler.class);
        var cotani = mock(Cotani.class);

        var module = DefaultEconomyModule.create(new EconomyModule.Context(plugin, storage, scheduler, cotani));

        try {
            assertNotNull(module.economyService());
            var captor = ArgumentCaptor.forClass(AutoCloseable.class);
            verify(cotani).register(captor.capture());
            assertNotNull(captor.getValue());
        } finally {
            module.close();
        }
    }

    @Test
    void shouldCreateModuleThroughPublicEntryPoint() {
        var plugin = newPlugin();
        var storage = mock(CotaniStorage.class);
        var scheduler = mock(PaperTaskScheduler.class);
        var cotani = mock(Cotani.class);

        var module = CotaniEconomy.create(new EconomyModule.Context(plugin, storage, scheduler, cotani));

        try {
            assertSame(module, module);
        } finally {
            module.close();
        }
    }

    @Test
    void shouldExposeSameServiceAcrossCalls() {
        var plugin = newPlugin();
        var storage = mock(CotaniStorage.class);
        var scheduler = mock(PaperTaskScheduler.class);
        var cotani = mock(Cotani.class);

        var module = DefaultEconomyModule.create(new EconomyModule.Context(plugin, storage, scheduler, cotani));

        try {
            assertSame(module.economyService(), module.economyService());
        } finally {
            module.close();
        }
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullContextAndContextComponents() {
        assertThrows(NullPointerException.class, () -> DefaultEconomyModule.create(null));
        assertThrows(NullPointerException.class, () -> CotaniEconomy.create(null));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyModule.Context(null, mock(CotaniStorage.class), mock(PaperTaskScheduler.class)));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyModule.Context(newPlugin(), null, mock(PaperTaskScheduler.class)));
        assertThrows(
                NullPointerException.class,
                () -> new EconomyModule.Context(newPlugin(), mock(CotaniStorage.class), null));
    }

    @Test
    void shouldCloseWithoutOwnedCotani() {
        var plugin = newPlugin();
        var storage = mock(CotaniStorage.class);
        var scheduler = mock(PaperTaskScheduler.class);
        var cotani = mock(Cotani.class);

        var module = DefaultEconomyModule.create(new EconomyModule.Context(plugin, storage, scheduler, cotani));

        module.close();
        module.close();
    }

    @Test
    void shouldBuildOwnCotaniWhenContextDoesNotProvideOne() {
        var plugin = newPlugin();
        var storage = mock(CotaniStorage.class);
        var scheduler = mock(PaperTaskScheduler.class);

        var module = DefaultEconomyModule.create(new EconomyModule.Context(plugin, storage, scheduler));

        assertNotNull(module);
        module.close();
    }

    private Plugin newPlugin() {
        var plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("economy-module-test"));
        return plugin;
    }
}
