package com.cotani;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class Cotani implements AutoCloseable, AsyncCloseable {
    private static final Duration DEFAULT_ASYNC_CLOSE_TIMEOUT = Duration.ofSeconds(10);
    private static final String CLOSEABLE_NULL_MSG = "Parameter 'closeable' must not be null";
    private static final String ASYNC_CLOSEABLE_NULL_MSG = "Parameter 'asyncCloseable' must not be null";

    private final Plugin plugin;
    private final List<AutoCloseable> closeables;
    private final List<Supplier<CompletionStage<Void>>> asyncCloseables;
    private final Duration asyncCloseTimeout;
    private final AtomicBoolean closed;
    private final AtomicReference<@Nullable CompletableFuture<Void>> closeFuture;
    private final Object lock = new Object();

    private Cotani(
            Plugin plugin,
            List<AutoCloseable> closeables,
            List<Supplier<CompletionStage<Void>>> asyncCloseables,
            Duration asyncCloseTimeout) {
        this.plugin = plugin;
        this.closeables = new ArrayList<>(closeables);
        this.asyncCloseables = new ArrayList<>(asyncCloseables);
        this.asyncCloseTimeout = asyncCloseTimeout;
        this.closed = new AtomicBoolean();
        this.closeFuture = new AtomicReference<>();
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
        Objects.requireNonNull(closeable, CLOSEABLE_NULL_MSG);

        synchronized (lock) {
            ensureNotClosed();

            if (closeable instanceof AsyncCloseable asyncCloseable) {
                asyncCloseables.add(asyncCloseable::closeAsync);
                return this;
            }
            closeables.add(closeable);
        }

        return this;
    }

    public Cotani deregister(AutoCloseable closeable) {
        Objects.requireNonNull(closeable, CLOSEABLE_NULL_MSG);

        synchronized (lock) {
            closeables.remove(closeable);
        }

        return this;
    }

    public Cotani registerAsync(Supplier<CompletionStage<Void>> asyncCloseable) {
        Objects.requireNonNull(asyncCloseable, ASYNC_CLOSEABLE_NULL_MSG);

        synchronized (lock) {
            ensureNotClosed();

            asyncCloseables.add(asyncCloseable);
        }

        return this;
    }

    public Cotani deregisterAsync(Supplier<CompletionStage<Void>> asyncCloseable) {
        Objects.requireNonNull(asyncCloseable, ASYNC_CLOSEABLE_NULL_MSG);

        synchronized (lock) {
            asyncCloseables.remove(asyncCloseable);
        }

        return this;
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("Cotani is already closed");
        }
    }

    /**
     * Executes resource teardown asynchronously.
     *
     * <p>First, all registered asynchronous teardown stages are executed concurrently.
     * Once completed, synchronous closeables are executed in reverse registration order.
     *
     * @return a stage completing when all teardown steps finish
     */
    @Override
    public CompletionStage<Void> closeAsync() {
        var result = new CompletableFuture<Void>();

        if (!closeFuture.compareAndSet(null, result)) {
            return Objects.requireNonNull(closeFuture.get(), "closeFuture");
        }

        List<Supplier<CompletionStage<Void>>> asyncSnapshot;
        List<AutoCloseable> syncSnapshot;
        synchronized (lock) {
            closed.set(true);
            asyncSnapshot = List.copyOf(asyncCloseables);
            syncSnapshot = List.copyOf(closeables);
            asyncCloseables.clear();
            closeables.clear();
        }

        var asyncTeardown = executeAsyncCloseables(asyncSnapshot).toCompletableFuture();
        var _ = asyncTeardown
                .orTimeout(asyncCloseTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((firstFailure, asyncErr) -> {
                    var combinedFailure = firstFailure;

                    if (asyncErr != null && combinedFailure == null) {
                        var cause = asyncErr instanceof java.util.concurrent.CompletionException completion
                                        && completion.getCause() != null
                                ? completion.getCause()
                                : asyncErr;
                        var message = cause instanceof TimeoutException
                                ? "Failed to close resource within timeout"
                                : "Failed to execute async closeables";
                        combinedFailure = new CotaniCloseException(message, cause);
                    }

                    combinedFailure = executeSyncCloseables(syncSnapshot, combinedFailure);

                    if (combinedFailure != null) {
                        result.completeExceptionally(combinedFailure);
                        return;
                    }

                    result.complete(null);
                });

        return result;
    }

    /**
     * Begins closing all registered resources without blocking the calling thread.
     *
     * <p>Use {@link #closeAsync()} when the caller needs to observe completion or failure.
     */
    @Override
    public void close() {
        closeAsync().whenComplete((_, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to close Cotani resources", failure);
            }
        });
    }

    private CompletionStage<@Nullable CotaniCloseException> executeAsyncCloseables(
            List<Supplier<CompletionStage<Void>>> suppliers) {
        if (suppliers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletionStage<Void>> stages = new ArrayList<>(suppliers.size());
        CotaniCloseException initialFailure = null;

        for (var supplier : suppliers.reversed()) {
            try {
                var stage = Objects.requireNonNull(supplier.get(), "Async closeable supplier returned null");
                stages.add(stage);
            } catch (Exception failure) {
                plugin.getLogger().log(Level.SEVERE, "Async closeable supplier failed", failure);
                initialFailure = mergeFailure(initialFailure, failure);
            }
        }

        if (stages.isEmpty()) {
            return CompletableFuture.completedFuture(initialFailure);
        }

        final var capturedFailure = initialFailure;

        return CompletableFuture.allOf(stages.stream()
                        .map(CompletionStage::toCompletableFuture)
                        .toArray(CompletableFuture[]::new))
                .handle((_, _) -> {
                    var current = capturedFailure;
                    for (var stage : stages) {
                        var future = stage.toCompletableFuture();
                        if (future.isCompletedExceptionally()) {
                            Throwable cause = future.exceptionNow();
                            plugin.getLogger().log(Level.SEVERE, "Async closeable failed", cause);
                            current = mergeFailure(current, cause);
                        }
                    }
                    return current;
                });
    }

    private @Nullable CotaniCloseException executeSyncCloseables(
            List<AutoCloseable> resources, @Nullable CotaniCloseException initialFailure) {
        CotaniCloseException currentFailure = initialFailure;
        int index = resources.size();

        for (var closeable : resources.reversed()) {
            index--;
            try {
                closeable.close();
            } catch (Exception failure) {
                plugin.getLogger()
                        .log(Level.SEVERE, "Failed to close resource #{0}: {1}", new Object[] {index, closeable});
                currentFailure = mergeFailure(currentFailure, failure);
            }
        }

        return currentFailure;
    }

    public static final class Builder {
        private final Plugin plugin;
        private final List<AutoCloseable> closeables = new ArrayList<>();
        private final List<Supplier<CompletionStage<Void>>> asyncCloseables = new ArrayList<>();
        private Duration asyncCloseTimeout = DEFAULT_ASYNC_CLOSE_TIMEOUT;
        private boolean built;

        private Builder(Plugin plugin) {
            this.plugin = plugin;
        }

        public Builder timeout(Duration timeout) {
            this.asyncCloseTimeout = Objects.requireNonNull(timeout, "Parameter 'timeout' must not be null");
            return this;
        }

        public Builder with(AutoCloseable closeable) {
            Objects.requireNonNull(closeable, CLOSEABLE_NULL_MSG);

            ensureOpen();

            if (closeable instanceof AsyncCloseable asyncCloseable) {
                asyncCloseables.add(asyncCloseable::closeAsync);
                return this;
            }
            closeables.add(closeable);

            return this;
        }

        /**
         * Registers an asynchronous teardown stage.
         *
         * <p>The supplier is invoked when {@link Cotani#close()} or {@link Cotani#closeAsync()} is called.
         */
        public Builder withAsync(Supplier<CompletionStage<Void>> asyncCloseable) {
            Objects.requireNonNull(asyncCloseable, ASYNC_CLOSEABLE_NULL_MSG);

            ensureOpen();

            asyncCloseables.add(asyncCloseable);

            return this;
        }

        public Cotani build() {
            ensureOpen();

            var instance = new Cotani(plugin, closeables, asyncCloseables, asyncCloseTimeout);
            this.built = true;
            closeables.clear();
            asyncCloseables.clear();

            return instance;
        }

        private void ensureOpen() {
            if (built) {
                throw new IllegalStateException(
                        "Builder has already been used; create a new Builder via Cotani.forPlugin(...)");
            }
        }
    }
}
