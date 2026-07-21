package com.cotani.storage.future;

import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * @deprecated Use {@link CompletionStage} directly. This adapter will be removed in a future release.
 */
@Deprecated
public final class OptionalStorageFuture<T> {

    private final StorageFuture<Optional<T>> source;
    private final PaperTaskScheduler scheduler;

    public OptionalStorageFuture(StorageFuture<Optional<T>> source, PaperTaskScheduler scheduler) {
        this.source = Objects.requireNonNull(source, "source");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public StorageFuture<Optional<T>> source() {
        return source;
    }

    public CompletionStage<Optional<T>> toCompletionStage() {
        return source.toCompletionStage();
    }

    public StorageFuture<T> fallback(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return new StorageFuture<>(
                source.toCompletionStage().thenApply(optional -> optional.orElseGet(supplier)), scheduler);
    }

    public StorageFuture<T> fallbackFuture(Supplier<StorageFuture<T>> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return new StorageFuture<>(
                source.toCompletionStage()
                        .thenCompose(optional -> optional.map(CompletableFuture::completedFuture)
                                .orElseGet(() -> supplier.get().raw())),
                scheduler);
    }
}
