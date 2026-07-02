package com.cotani.task.api;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class TaskFuture<T> {

    private final CompletableFuture<T> future;
    private final PaperTaskScheduler scheduler;

    public TaskFuture(CompletableFuture<T> future, PaperTaskScheduler scheduler) {
        this.future = Objects.requireNonNull(future, "future");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public static <T> TaskFuture<T> completed(@Nullable T value, PaperTaskScheduler scheduler) {
        return new TaskFuture<>(CompletableFuture.completedFuture(value), scheduler);
    }

    public static <T> TaskFuture<T> failed(Throwable throwable, PaperTaskScheduler scheduler) {
        return new TaskFuture<>(CompletableFuture.failedFuture(throwable), scheduler);
    }

    public static <T> TaskFuture<T> from(CompletableFuture<T> future, PaperTaskScheduler scheduler) {
        return new TaskFuture<>(future, scheduler);
    }

    public static TaskFuture<Void> allOf(Collection<? extends TaskFuture<?>> futures, PaperTaskScheduler scheduler) {
        var completableFutures =
                futures.stream().map(TaskFuture::toCompletableFuture).toArray(CompletableFuture[]::new);
        return new TaskFuture<>(CompletableFuture.allOf(completableFutures), scheduler);
    }

    public PaperTaskScheduler scheduler() {
        return scheduler;
    }

    public CompletionStage<T> toCompletionStage() {
        return future;
    }

    private CompletableFuture<T> toCompletableFuture() {
        return future;
    }

    public TaskChain<T> asTaskChain() {
        return scheduler.chain(future);
    }

    public <R> TaskFuture<R> map(Function<T, R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return new TaskFuture<>(future.thenApplyAsync(mapper, scheduler.asyncExecutor()), scheduler);
    }

    public <R> TaskFuture<R> flatMap(Function<T, TaskFuture<R>> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return new TaskFuture<>(
                future.thenComposeAsync(
                        value -> mapper.apply(value).toCompletionStage().toCompletableFuture(),
                        scheduler.asyncExecutor()),
                scheduler);
    }

    public TaskFuture<T> fallback(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return new TaskFuture<>(future.exceptionallyAsync(_ -> supplier.get(), scheduler.asyncExecutor()), scheduler);
    }

    public TaskFuture<T> onFailure(Consumer<Throwable> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        var handled = future.exceptionallyAsync(throwable -> {
            var unwrapped = unwrap(throwable);
            runSafe("onFailure", () -> consumer.accept(unwrapped));
            throw asCompletionException(unwrapped);
        }, scheduler.asyncExecutor());
        return new TaskFuture<>(handled, scheduler);
    }

    public TaskFuture<T> onFailureAsync(Consumer<Throwable> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        var handled = future.whenCompleteAsync((_, throwable) -> {
            if (throwable != null) {
                runSafe("onFailureAsync", () -> consumer.accept(unwrap(throwable)));
            }
        }, scheduler.asyncExecutor());
        return new TaskFuture<>(handled, scheduler);
    }

    public TaskFuture<T> onFailureGlobal(Consumer<Throwable> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        var handled = future.whenCompleteAsync((_, throwable) -> {
            if (throwable != null) {
                runSafe("onFailureGlobal", () -> consumer.accept(unwrap(throwable)));
            }
        }, scheduler.globalExecutor());
        return new TaskFuture<>(handled, scheduler);
    }

    /**
     * @deprecated prefer {@link #onFailureEntity(UUID, Consumer)} to avoid retaining live Bukkit objects.
     */
    @Deprecated
    public TaskFuture<T> onFailureEntity(Entity entity, Consumer<Throwable> consumer) {
        Objects.requireNonNull(entity, "entity");
        return onFailureEntity(entity.getUniqueId(), consumer);
    }

    public TaskFuture<T> onFailureEntity(UUID entityId, Consumer<Throwable> consumer) {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(consumer, "consumer");
        var handled = future.whenCompleteAsync((_, throwable) -> {
            if (throwable == null) {
                return;
            }
            var unwrapped = unwrap(throwable);
            scheduler.entity(entityId, () -> runSafe("onFailureEntity", () -> consumer.accept(unwrapped)));
        }, scheduler.asyncExecutor());
        return new TaskFuture<>(handled, scheduler);
    }

    /**
     * @deprecated prefer {@link #onFailureRegion(UUID, int, int, Consumer)} to avoid retaining live Bukkit objects.
     */
    @Deprecated
    public TaskFuture<T> onFailureRegion(Location location, Consumer<Throwable> consumer) {
        Objects.requireNonNull(location, "location");
        var world = Objects.requireNonNull(location.getWorld(), "location.world");
        return onFailureRegion(world.getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4, consumer);
    }

    public TaskFuture<T> onFailureRegion(UUID worldId, int chunkX, int chunkZ, Consumer<Throwable> consumer) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(consumer, "consumer");
        var handled = future.whenCompleteAsync((_, throwable) -> {
            if (throwable == null) {
                return;
            }
            var unwrapped = unwrap(throwable);
            scheduler.region(worldId, chunkX, chunkZ, () -> runSafe("onFailureRegion", () -> consumer.accept(unwrapped)));
        }, scheduler.asyncExecutor());
        return new TaskFuture<>(handled, scheduler);
    }

    public TaskFuture<T> thenAsync(Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        var handled = future.whenCompleteAsync((value, throwable) -> {
            if (throwable == null) {
                runSafe("thenAsync", () -> consumer.accept(value));
            }
        }, scheduler.asyncExecutor());
        return new TaskFuture<>(handled, scheduler);
    }

    public TaskFuture<T> thenGlobal(Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        var handled = future.whenCompleteAsync((value, throwable) -> {
            if (throwable == null) {
                runSafe("thenGlobal", () -> consumer.accept(value));
            }
        }, scheduler.globalExecutor());
        return new TaskFuture<>(handled, scheduler);
    }

    /**
     * @deprecated prefer {@link #thenEntity(UUID, Consumer)} to avoid retaining live Bukkit objects.
     */
    @Deprecated
    public TaskFuture<T> thenEntity(Entity entity, Consumer<T> consumer) {
        Objects.requireNonNull(entity, "entity");
        return thenEntity(entity.getUniqueId(), consumer);
    }

    public TaskFuture<T> thenEntity(UUID entityId, Consumer<T> consumer) {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(consumer, "consumer");
        var handled = future.whenCompleteAsync((value, throwable) -> {
            if (throwable != null) {
                return;
            }
            scheduler.entity(entityId, () -> runSafe("thenEntity", () -> consumer.accept(value)));
        }, scheduler.asyncExecutor());
        return new TaskFuture<>(handled, scheduler);
    }

    /**
     * @deprecated prefer {@link #thenRegion(UUID, int, int, Consumer)} to avoid retaining live Bukkit objects.
     */
    @Deprecated
    public TaskFuture<T> thenRegion(Location location, Consumer<T> consumer) {
        Objects.requireNonNull(location, "location");
        var world = Objects.requireNonNull(location.getWorld(), "location.world");
        return thenRegion(world.getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4, consumer);
    }

    public TaskFuture<T> thenRegion(UUID worldId, int chunkX, int chunkZ, Consumer<T> consumer) {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(consumer, "consumer");
        var handled = future.whenCompleteAsync((value, throwable) -> {
            if (throwable != null) {
                return;
            }
            scheduler.region(worldId, chunkX, chunkZ, () -> runSafe("thenRegion", () -> consumer.accept(value)));
        }, scheduler.asyncExecutor());
        return new TaskFuture<>(handled, scheduler);
    }

    private void runSafe(String callbackName, Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception callbackError) {
            report(callbackName, callbackError);
        }
    }

    private void report(String callbackName, Throwable throwable) {
        try {
            var metadata = TaskMetadata.named("task-future-" + callbackName, ExecutionTarget.async());
            scheduler.exceptionHandler().handle(TaskContext.start(metadata), throwable);
        } catch (Exception ignored) {
            // best-effort error reporting
        }
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
}