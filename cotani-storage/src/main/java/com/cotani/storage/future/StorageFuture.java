package com.cotani.storage.future;

import com.cotani.task.api.PaperTaskScheduler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class StorageFuture<T> {

    private final CompletableFuture<T> future;
    private final PaperTaskScheduler scheduler;

    public StorageFuture(CompletableFuture<T> future, PaperTaskScheduler scheduler) {
        this.future = future;
        this.scheduler = scheduler;
    }

    public StorageFuture(CompletionStage<T> stage, PaperTaskScheduler scheduler) {
        this(stage.toCompletableFuture(), scheduler);
    }

    public static <T> StorageFuture<T> completed(@Nullable T value, PaperTaskScheduler scheduler) {
        return new StorageFuture<>(CompletableFuture.completedFuture(value), scheduler);
    }

    public static <T> StorageFuture<T> failed(Throwable error, PaperTaskScheduler scheduler) {
        return new StorageFuture<>(CompletableFuture.failedFuture(error), scheduler);
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    private static CompletionException asCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException completionException) {
            return completionException;
        }
        return new CompletionException(throwable);
    }

    public PaperTaskScheduler scheduler() {
        return scheduler;
    }

    public CompletionStage<T> toCompletionStage() {
        return future;
    }

    CompletableFuture<T> raw() {
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
        return new StorageFuture<>(future.exceptionally(_ -> supplier.get()), scheduler);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public StorageFuture<T> onFailure(Consumer<Throwable> consumer) {
        var handled = future.exceptionally(throwable -> {
            var unwrapped = unwrap(throwable);
            consumer.accept(unwrapped);
            throw asCompletionException(unwrapped);
        });
        return new StorageFuture<>(handled, scheduler);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public StorageFuture<T> onFailureAsync(Consumer<Throwable> consumer) {
        future.whenComplete((_, throwable) -> {
            if (throwable != null) {
                scheduler.async(() -> consumer.accept(unwrap(throwable)));
            }
        });
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public StorageFuture<T> thenAsync(Consumer<T> consumer) {
        future.whenComplete((value, throwable) -> {
            if (throwable == null) {
                scheduler.async(() -> consumer.accept(value));
            }
        });
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public StorageFuture<T> thenGlobal(Consumer<T> consumer) {
        future.whenComplete((value, throwable) -> {
            if (throwable == null) {
                scheduler.global(() -> consumer.accept(value));
            }
        });
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public StorageFuture<T> thenEntity(Entity entity, Consumer<T> consumer) {
        future.whenComplete((value, throwable) -> {
            if (throwable == null) {
                try {
                    scheduler.entity(entity, () -> consumer.accept(value));
                } catch (RuntimeException ignored) {
                    // Entity callbacks are best-effort because the entity can retire before completion.
                }
            }
        });
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public StorageFuture<T> thenRegion(Location location, Consumer<T> consumer) {
        future.whenComplete((value, throwable) -> {
            if (throwable == null) {
                try {
                    scheduler.region(location, () -> consumer.accept(value));
                } catch (RuntimeException ignored) {
                    // Region callbacks are best-effort because the region can unload before completion.
                }
            }
        });
        return this;
    }
}
