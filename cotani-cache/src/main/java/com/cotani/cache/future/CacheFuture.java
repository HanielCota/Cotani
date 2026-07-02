package com.cotani.cache.future;

import com.cotani.task.api.PaperTaskScheduler;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;

public final class CacheFuture<T> {

    private final CompletableFuture<T> future;
    private final PaperTaskScheduler scheduler;

    public CacheFuture(CompletableFuture<T> future, PaperTaskScheduler scheduler) {
        this.future = Objects.requireNonNull(future, "future");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public static <T> CacheFuture<T> completed(@Nullable T value, PaperTaskScheduler scheduler) {
        return new CacheFuture<>(CompletableFuture.completedFuture(value), scheduler);
    }

    public static <T> CacheFuture<T> failed(Throwable throwable, PaperTaskScheduler scheduler) {
        return new CacheFuture<>(CompletableFuture.failedFuture(throwable), scheduler);
    }

    public static <T> CacheFuture<T> from(CompletableFuture<T> future, PaperTaskScheduler scheduler) {
        return new CacheFuture<>(future, scheduler);
    }

    public static CacheFuture<Void> allOf(Collection<? extends CacheFuture<?>> futures, PaperTaskScheduler scheduler) {
        var completableFutures =
                futures.stream().map(CacheFuture::toCompletableFuture).toArray(CompletableFuture[]::new);
        return new CacheFuture<>(CompletableFuture.allOf(completableFutures), scheduler);
    }

    public <U> CacheFuture<U> map(Function<T, U> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return new CacheFuture<>(future.thenApplyAsync(mapper, scheduler.asyncExecutor()), scheduler);
    }

    public <U> CacheFuture<U> flatMap(Function<T, CacheFuture<U>> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        return new CacheFuture<>(
                future.thenComposeAsync(value -> mapper.apply(value).toCompletableFuture(), scheduler.asyncExecutor()),
                scheduler);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public CacheFuture<T> thenAsync(Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        future.whenCompleteAsync(
                (value, throwable) -> {
                    if (throwable == null) {
                        scheduler.async(() -> consumer.accept(value));
                    }
                },
                scheduler.asyncExecutor());
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public CacheFuture<T> thenGlobal(Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        future.whenCompleteAsync(
                (value, throwable) -> {
                    if (throwable == null) {
                        scheduler.global(() -> consumer.accept(value));
                    }
                },
                scheduler.asyncExecutor());
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public CacheFuture<T> thenEntity(Entity entity, Consumer<T> consumer) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(consumer, "consumer");
        future.whenCompleteAsync(
                (value, throwable) -> {
                    if (throwable == null) {
                        try {
                            scheduler.entity(entity, () -> consumer.accept(value));
                        } catch (RuntimeException ignored) {
                            // Entity callbacks are best-effort because the entity can retire before completion.
                        }
                    }
                },
                scheduler.asyncExecutor());
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public CacheFuture<T> thenRegion(Location location, Consumer<T> consumer) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(consumer, "consumer");
        future.whenCompleteAsync(
                (value, throwable) -> {
                    if (throwable == null) {
                        try {
                            scheduler.region(location, () -> consumer.accept(value));
                        } catch (RuntimeException ignored) {
                            // Region callbacks are best-effort because the region can unload before completion.
                        }
                    }
                },
                scheduler.asyncExecutor());
        return this;
    }

    public CacheFuture<T> onFailure(Consumer<Throwable> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        var handled = future.exceptionallyAsync(
                throwable -> {
                    var unwrapped = unwrap(throwable);
                    runSafe(() -> consumer.accept(unwrapped));
                    throw asCompletionException(unwrapped);
                },
                scheduler.asyncExecutor());
        return new CacheFuture<>(handled, scheduler);
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public CacheFuture<T> onFailureGlobal(Consumer<Throwable> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        future.whenCompleteAsync(
                (_, throwable) -> {
                    if (throwable != null) {
                        scheduler.global(() -> consumer.accept(unwrap(throwable)));
                    }
                },
                scheduler.asyncExecutor());
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public CacheFuture<T> onFailureEntity(Entity entity, Consumer<Throwable> consumer) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(consumer, "consumer");
        future.whenCompleteAsync(
                (_, throwable) -> {
                    if (throwable != null) {
                        try {
                            scheduler.entity(entity, () -> consumer.accept(unwrap(throwable)));
                        } catch (RuntimeException ignored) {
                            // Entity callbacks are best-effort because the entity can retire before completion.
                        }
                    }
                },
                scheduler.asyncExecutor());
        return this;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public CacheFuture<T> onFailureRegion(Location location, Consumer<Throwable> consumer) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(consumer, "consumer");
        future.whenCompleteAsync(
                (_, throwable) -> {
                    if (throwable != null) {
                        try {
                            scheduler.region(location, () -> consumer.accept(unwrap(throwable)));
                        } catch (RuntimeException ignored) {
                            // Region callbacks are best-effort because the region can unload before completion.
                        }
                    }
                },
                scheduler.asyncExecutor());
        return this;
    }

    public CompletionStage<T> toCompletionStage() {
        return future;
    }

    public CompletableFuture<T> toCompletableFuture() {
        return future;
    }

    public PaperTaskScheduler scheduler() {
        return scheduler;
    }

    private void runSafe(Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ignored) {
            // best-effort callback error handling
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
