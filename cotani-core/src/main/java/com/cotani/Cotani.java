package com.cotani;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class Cotani implements AutoCloseable {

    private static final Duration ASYNC_CLOSE_TIMEOUT = Duration.ofSeconds(10);

    private final Plugin plugin;
    private final CopyOnWriteArrayList<AutoCloseable> closeables;
    private final CopyOnWriteArrayList<Supplier<CompletionStage<Void>>> asyncCloseables;
    private final AtomicBoolean closed;

    private Cotani(
            Plugin plugin, List<AutoCloseable> closeables, List<Supplier<CompletionStage<Void>>> asyncCloseables) {
        this.plugin = plugin;
        this.closeables = new CopyOnWriteArrayList<>(closeables);
        this.asyncCloseables = new CopyOnWriteArrayList<>(asyncCloseables);
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

        firstFailure = closeAsyncCloseables(firstFailure);

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
        asyncCloseables.clear();

        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private @Nullable CotaniCloseException closeAsyncCloseables(@Nullable CotaniCloseException firstFailure) {
        if (asyncCloseables.isEmpty()) {
            return firstFailure;
        }

        List<CompletionStage<Void>> stages = new ArrayList<>(asyncCloseables.size());
        for (var supplier : asyncCloseables.reversed()) {
            try {
                stages.add(supplier.get());
            } catch (Exception failure) {
                plugin.getLogger().log(Level.SEVERE, "Async closeable supplier failed", failure);
                firstFailure = mergeFailure(firstFailure, failure);
            }
        }

        if (stages.isEmpty()) {
            return firstFailure;
        }

        try {
            CompletableFuture.allOf(stages.stream()
                            .map(CompletionStage::toCompletableFuture)
                            .toArray(CompletableFuture[]::new))
                    .get(ASYNC_CLOSE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception failure) {
            plugin.getLogger()
                    .log(Level.SEVERE, "Async closeables did not complete within " + ASYNC_CLOSE_TIMEOUT, failure);
            firstFailure = mergeFailure(firstFailure, failure);
        }

        return firstFailure;
    }

    public static final class Builder {

        private final Plugin plugin;
        private final List<AutoCloseable> closeables = new ArrayList<>();
        private final List<Supplier<CompletionStage<Void>>> asyncCloseables = new ArrayList<>();
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

        /**
         * Registers an asynchronous teardown stage.
         *
         * <p>The supplier is invoked when {@link Cotani#close()} is called. All async closeables run
         * concurrently and are given {@value #ASYNC_CLOSE_TIMEOUT} seconds to complete.
         */
        public Builder withAsync(Supplier<CompletionStage<Void>> closeable) {
            Objects.requireNonNull(closeable, "Parameter 'closeable' must not be null");
            ensureOpen();

            asyncCloseables.add(closeable);

            return this;
        }

        public Cotani build() {
            ensureOpen();

            var built = new Cotani(plugin, closeables, asyncCloseables);
            this.built = true;
            closeables.clear();
            asyncCloseables.clear();
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
