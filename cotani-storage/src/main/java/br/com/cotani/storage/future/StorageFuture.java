package br.com.cotani.storage.future;

import br.com.cotani.storage.scheduler.CotaniScheduler;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class StorageFuture<T> {

    private final CompletableFuture<T> future;
    private final CotaniScheduler scheduler;

    public StorageFuture(CompletableFuture<T> future, CotaniScheduler scheduler) {
        this.future = future;
        this.scheduler = scheduler;
    }

    public static <T> StorageFuture<T> completed(T value, CotaniScheduler scheduler) {
        return new StorageFuture<>(CompletableFuture.completedFuture(value), scheduler);
    }

    public CompletableFuture<T> raw() {
        return future;
    }

    public <R> StorageFuture<R> map(Function<T, R> mapper) {
        return new StorageFuture<>(future.thenApply(mapper), scheduler);
    }

    public <R> StorageFuture<R> flatMap(Function<T, StorageFuture<R>> mapper) {
        return new StorageFuture<>(
                future.thenCompose(value -> mapper.apply(value).raw()), scheduler);
    }

    public StorageFuture<T> fallback(Supplier<T> supplier) {
        return new StorageFuture<>(future.exceptionally(throwable -> supplier.get()), scheduler);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public StorageFuture<T> onFailure(Consumer<Throwable> consumer) {
        future.exceptionally(throwable -> {
            consumer.accept(throwable);
            return null;
        });
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public StorageFuture<T> onFailureAsync(Consumer<Throwable> consumer) {
        future.exceptionally(throwable -> {
            scheduler.async(() -> consumer.accept(throwable));
            return null;
        });
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public StorageFuture<T> thenAsync(Consumer<T> consumer) {
        future.thenAccept(value -> scheduler.async(() -> consumer.accept(value)));
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public StorageFuture<T> thenGlobal(Consumer<T> consumer) {
        future.thenAccept(value -> scheduler.global(() -> consumer.accept(value)));
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public StorageFuture<T> thenEntity(Entity entity, Consumer<T> consumer) {
        future.thenAccept(value -> scheduler.entity(entity, () -> consumer.accept(value)));
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public StorageFuture<T> thenRegion(Location location, Consumer<T> consumer) {
        future.thenAccept(value -> scheduler.region(location, () -> consumer.accept(value)));
        return this;
    }
}
