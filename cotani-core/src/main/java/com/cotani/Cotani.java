package com.cotani;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class Cotani implements AutoCloseable {

    private final Plugin plugin;
    private List<AutoCloseable> closeables;

    private Cotani(Plugin plugin, List<AutoCloseable> closeables) {
        this.plugin = plugin;
        this.closeables = List.copyOf(closeables);
    }

    public static Builder forPlugin(Plugin plugin) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");

        return new Builder(plugin);
    }

    public Plugin plugin() {
        return plugin;
    }

    @Override
    public void close() {
        RuntimeException firstFailure = null;

        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception exception) {
                firstFailure = mergeFailure(firstFailure, exception);
            }
        }

        closeables = List.of();

        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private static RuntimeException mergeFailure(@Nullable RuntimeException firstFailure, Exception exception) {
        if (firstFailure == null) {
            if (exception instanceof RuntimeException runtimeException) {
                return runtimeException;
            }

            return new RuntimeException("Failed to close resource", exception);
        }

        firstFailure.addSuppressed(exception);

        return firstFailure;
    }

    public static final class Builder {

        private final Plugin plugin;
        private final List<AutoCloseable> closeables = new ArrayList<>();

        private Builder(Plugin plugin) {
            this.plugin = plugin;
        }

        public Builder with(AutoCloseable closeable) {
            Objects.requireNonNull(closeable, "Parameter 'closeable' must not be null");
            closeables.add(closeable);

            return this;
        }

        public Cotani build() {
            return new Cotani(plugin, closeables);
        }
    }
}
