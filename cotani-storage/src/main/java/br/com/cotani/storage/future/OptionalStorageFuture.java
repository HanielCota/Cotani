package br.com.cotani.storage.future;

import br.com.cotani.storage.scheduler.CotaniScheduler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class OptionalStorageFuture<T> {

    private final StorageFuture<Optional<T>> source;
    private final CotaniScheduler scheduler;

    public OptionalStorageFuture(StorageFuture<Optional<T>> source, CotaniScheduler scheduler) {
        this.source = source;
        this.scheduler = scheduler;
    }

    public StorageFuture<Optional<T>> source() {
        return source;
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
