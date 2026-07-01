package com.cotani;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CotaniTest {

    @Test
    void forPluginReturnsBuilder() {
        var plugin = Mockito.mock(Plugin.class);

        var builder = Cotani.forPlugin(plugin);

        assertSame(plugin, builder.build().plugin());
    }

    @Test
    void closeClosesRegisteredResources() {
        var plugin = Mockito.mock(Plugin.class);
        var closed = new AtomicBoolean();
        var cotani = Cotani.forPlugin(plugin).with(() -> closed.set(true)).build();

        cotani.close();

        assertTrue(closed.get());
    }

    @Test
    void closeAggregatesExceptions() {
        var plugin = Mockito.mock(Plugin.class);
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

        var thrown = assertThrows(RuntimeException.class, cotani::close);

        assertEquals("first", thrown.getMessage());
        assertEquals("second", thrown.getSuppressed()[0].getMessage());
    }

    @Test
    void closeWrapsCheckedExceptions() {
        var plugin = Mockito.mock(Plugin.class);
        var cotani = Cotani.forPlugin(plugin)
                .with(() -> {
                    throw new Exception("checked");
                })
                .build();

        var thrown = assertThrows(RuntimeException.class, cotani::close);
        var cause = thrown.getCause();

        assertEquals("Failed to close resource", thrown.getMessage());
        assertEquals("checked", cause == null ? "" : cause.getMessage());
    }
}
