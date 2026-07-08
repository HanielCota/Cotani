package com.cotani;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class Cotani implements AutoCloseable {

    private final Plugin plugin;
    private final CopyOnWriteArrayList<AutoCloseable> closeables;
    private final AtomicBoolean closed;

    private Cotani(Plugin plugin, List<AutoCloseable> closeables) {
        this.plugin = plugin;
        this.closeables = new CopyOnWriteArrayList<>(closeables);
        this.closed = new AtomicBoolean();
    }

    public static Builder forPlugin(Plugin plugin) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");

        return new Builder(plugin);
    }

    private static CotaniCloseException mergeFailure(@Nullable CotaniCloseException firstFailure, Throwable failure) {
        if (firstFailure == null) {
            return new CotaniCloseException("Failed to close resource", failure);
        }

        firstFailure.addSuppressed(failure);

        return firstFailure;
    }

    public Plugin plugin() {
        return plugin;
    }

    public Cotani register(AutoCloseable closeable) {
        Objects.requireNonNull(closeable, "Parameter 'closeable' must not be null");

        if (closed.get()) {
            throw new IllegalStateException("Cotani is already closed");
        }

        closeables.add(closeable);
        return this;
    }

    public Cotani deregister(AutoCloseable closeable) {
        Objects.requireNonNull(closeable, "Parameter 'closeable' must not be null");

        closeables.remove(closeable);
        return this;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        CotaniCloseException firstFailure = null;
        var resources = closeables;

        int index = resources.size();
        for (var closeable : resources.reversed()) {
            index--;
            try {
                closeable.close();
            } catch (Exception failure) {
                plugin.getLogger()
                        .log(Level.SEVERE, "Failed to close resource #{0}: {1}", new Object[] {index, closeable});
                firstFailure = mergeFailure(firstFailure, failure);
            }
        }
        resources.clear();

        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    public static final class Builder {

        private final Plugin plugin;
        private final List<AutoCloseable> closeables = new ArrayList<>();
        private boolean built;

        private Builder(Plugin plugin) {
            this.plugin = plugin;
        }

        public Builder with(AutoCloseable closeable) {
            Objects.requireNonNull(closeable, "Parameter 'closeable' must not be null");
            ensureOpen();

            closeables.add(closeable);

            return this;
        }

        public Cotani build() {
            ensureOpen();

            var built = new Cotani(plugin, closeables);
            this.built = true;
            closeables.clear();
            return built;
        }

        private void ensureOpen() {
            if (built) {
                throw new IllegalStateException(
                        "Builder has already been used; create a new Builder via Cotani.forPlugin(...)");
            }
        }
    }
}
