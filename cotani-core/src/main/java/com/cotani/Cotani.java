package com.cotani;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class Cotani implements AutoCloseable {

    private static final List<AutoCloseable> EMPTY = List.of();

    private final Plugin plugin;
    private final AtomicReference<List<AutoCloseable>> closeables;
    private final AtomicBoolean closed;

    private Cotani(Plugin plugin, List<AutoCloseable> closeables) {
        this.plugin = plugin;
        this.closeables = new AtomicReference<>(List.copyOf(closeables));
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

        closeables.updateAndGet(current -> {
            var next = new ArrayList<AutoCloseable>(current.size() + 1);
            next.addAll(current);
            next.add(closeable);
            return List.copyOf(next);
        });

        return this;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        CotaniCloseException firstFailure = null;
        var resources = closeables.getAndSet(EMPTY);

        int index = resources.size();
        for (var closeable : resources.reversed()) {
            index--;
            try {
                closeable.close();
            } catch (Exception failure) {
                plugin.getLogger()
                        .log(java.util.logging.Level.SEVERE, "Failed to close resource #{0}: {1}", new Object[] {
                            index, closeable
                        });
                firstFailure = mergeFailure(firstFailure, failure);
            }
        }

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
