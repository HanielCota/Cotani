package com.cotani;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CotaniTest {

    private static Plugin pluginWithLogger() {
        var plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("cotani-test"));
        return plugin;
    }

    @Test
    void forPluginReturnsBuilder() {
        var plugin = pluginWithLogger();

        var builder = Cotani.forPlugin(plugin);

        assertSame(plugin, builder.build().plugin());
    }

    @Test
    @SuppressWarnings("NullAway")
    void forPluginRejectsNull() {
        assertThrows(NullPointerException.class, () -> Cotani.forPlugin(null));
    }

    @Test
    void closeClosesRegisteredResources() {
        var plugin = pluginWithLogger();
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin).with(() -> closed.set(true)).build();

        cotani.close();

        assertTrue(closed.get());
    }

    @Test
    void closeClosesResourcesInReverseOrder() {
        var plugin = pluginWithLogger();
        var order = new ArrayList<String>();
        var cotani = Cotani.forPlugin(plugin)
                .with(() -> order.add("first"))
                .with(() -> order.add("second"))
                .build();

        cotani.close();

        assertEquals(List.of("second", "first"), order);
    }

    @Test
    void closeAggregatesExceptions() {
        var plugin = pluginWithLogger();
        var first = new RuntimeException("first");
        var second = new RuntimeException("second");
        var cotani = Cotani.forPlugin(plugin)
                .with(() -> {
                    throw first;
                })
                .with(() -> {
                    throw second;
                })
                .build();

        var thrown = assertThrows(CotaniCloseException.class, cotani::close);
        var cause = thrown.getCause();

        assertEquals("Failed to close resource", thrown.getMessage());
        assertEquals("second", cause != null ? cause.getMessage() : "");
        assertEquals("first", thrown.getSuppressed()[0].getMessage());
    }

    @Test
    void closeWrapsCheckedExceptions() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin)
                .with(() -> {
                    throw new Exception("checked");
                })
                .build();

        var thrown = assertThrows(CotaniCloseException.class, cotani::close);
        var cause = thrown.getCause();

        assertEquals("Failed to close resource", thrown.getMessage());
        assertEquals("checked", cause == null ? "" : cause.getMessage());
    }

    @Test
    void closeContinuesAfterException() {
        var plugin = pluginWithLogger();
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin)
                .with(() -> closed.set(true))
                .with(() -> {
                    throw new RuntimeException("boom");
                })
                .build();

        assertThrows(CotaniCloseException.class, cotani::close);
        assertTrue(closed.get());
    }

    @Test
    void closeIsIdempotent() {
        var plugin = pluginWithLogger();
        var count = new AtomicInteger();
        var cotani = Cotani.forPlugin(plugin).with(count::incrementAndGet).build();

        cotani.close();
        cotani.close();

        assertEquals(1, count.get());
    }

    @Test
    void closeWithNoResourcesDoesNotThrow() {
        var plugin = pluginWithLogger();

        assertDoesNotThrow(Cotani.forPlugin(plugin).build()::close);
    }

    @Test
    void registerAddsResourceClosedByClose() {
        var plugin = pluginWithLogger();
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin).build();

        cotani.register(() -> closed.set(true));
        cotani.close();

        assertTrue(closed.get());
    }

    @Test
    void registerAfterCloseThrows() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin).build();
        cotani.close();

        assertThrows(IllegalStateException.class, () -> cotani.register(() -> {}));
    }

    @Test
    void builderIsSingleUse() {
        var plugin = pluginWithLogger();
        var builder = Cotani.forPlugin(plugin).with(() -> {});

        builder.build();

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void builderWithAfterBuildThrows() {
        var plugin = pluginWithLogger();
        var builder = Cotani.forPlugin(plugin).with(() -> {});

        builder.build();

        assertThrows(IllegalStateException.class, () -> builder.with(() -> {}));
    }

    @Test
    @SuppressWarnings("NullAway")
    void builderWithRejectsNull() {
        var plugin = pluginWithLogger();

        assertThrows(NullPointerException.class, () -> Cotani.forPlugin(plugin).with(null));
    }

    @Test
    void failureCarriesCauseAndSuppressed() {
        var plugin = pluginWithLogger();
        var cotani = Cotani.forPlugin(plugin)
                .with(() -> {
                    throw new IllegalStateException("a");
                })
                .with(() -> {
                    throw new IllegalStateException("b");
                })
                .build();

        var thrown = assertThrows(CotaniCloseException.class, cotani::close);

        assertNotNull(thrown.getCause());
        assertEquals(1, thrown.getSuppressed().length);
        assertNotEquals(thrown.getCause(), thrown.getSuppressed()[0]);
    }
}
