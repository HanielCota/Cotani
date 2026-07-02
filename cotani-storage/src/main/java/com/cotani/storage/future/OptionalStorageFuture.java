package com.cotani.storage.future;

import com.cotani.task.api.PaperTaskScheduler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class OptionalStorageFuture<T> {

    private final StorageFuture<Optional<T>> source;
    private final PaperTaskScheduler scheduler;

    public OptionalStorageFuture(StorageFuture<Optional<T>> source, PaperTaskScheduler scheduler) {
        this.source = source;
        this.scheduler = scheduler;
    }

    public StorageFuture<Optional<T>> source() {
        return source;
    }

    public CompletionStage<Optional<T>> toCompletionStage() {
        return source.toCompletionStage();
    }

    public StorageFuture<T> fallback(Supplier<T> supplier) {
        return new StorageFuture<>(source.raw().thenApply(optional -> optional.orElseGet(supplier)), scheduler);
    }

    public StorageFuture<T> fallbackFuture(Supplier<StorageFuture<T>> supplier) {
        return new StorageFuture<>(
                source.raw()
                        .thenCompose(optional -> optional.map(CompletableFuture::completedFuture)
                                .orElseGet(() -> supplier.get().raw())),
                scheduler);
    }
}
