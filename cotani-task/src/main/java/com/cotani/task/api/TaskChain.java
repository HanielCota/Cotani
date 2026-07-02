package com.cotani.task.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface TaskChain<T> {

    @SafeVarargs
    @SuppressWarnings("varargs")
    static <T> TaskChain<List<T>> allOf(PaperTaskScheduler scheduler, TaskChain<T>... chains) {
        if (chains.length == 0) {
            return scheduler.chain(CompletableFuture.completedFuture(List.of()));
        }

        List<CompletionStage<T>> stages =
                Arrays.stream(chains).map(TaskChain::toCompletionStage).toList();

        CompletionStage<List<T>> all = stages.stream()
                .reduce(
                        CompletableFuture.completedFuture(List.of()),
                        (acc, stage) -> acc.thenCombineAsync(
                                stage,
                                (list, value) -> {
                                    var next = new ArrayList<T>(list.size() + 1);
                                    next.addAll(list);
                                    next.add(value);
                                    return List.copyOf(next);
                                },
                                scheduler.asyncExecutor()),
                        (left, right) -> left.thenCombineAsync(
                                right,
                                (l, r) -> {
                                    var next = new ArrayList<T>(l.size() + r.size());
                                    next.addAll(l);
                                    next.addAll(r);
                                    return List.copyOf(next);
                                },
                                scheduler.asyncExecutor()));

        return scheduler.chain(all);
    }

    @SafeVarargs
    @SuppressWarnings({"varargs", "unchecked"})
    static <T> TaskChain<T> anyOf(PaperTaskScheduler scheduler, TaskChain<T>... chains) {
        if (chains.length == 0) {
            throw new IllegalArgumentException("chains must not be empty");
        }

        CompletableFuture<Object> any = CompletableFuture.anyOf(Arrays.stream(chains)
                .map(chain -> chain.toCompletionStage().toCompletableFuture())
                .toArray(CompletableFuture[]::new));

        CompletableFuture<T> typed = any.thenApplyAsync(value -> (T) value, scheduler.asyncExecutor());

        return scheduler.chain(typed);
    }

    <U> TaskChain<U> thenAsync(Function<T, U> function);

    <U> TaskChain<U> thenGlobal(Function<T, U> function);

    <U> TaskChain<U> thenRegion(Location location, Function<T, U> function);

    <U> TaskChain<U> thenRegion(UUID worldId, int chunkX, int chunkZ, Function<T, U> function);

    <U> TaskChain<U> thenEntity(Entity entity, Function<T, U> function);

    <U> TaskChain<U> thenEntity(UUID entityId, Function<T, U> function);

    TaskChain<T> consumeAsync(Consumer<T> consumer);

    TaskChain<T> consumeGlobal(Consumer<T> consumer);

    TaskChain<T> consumeRegion(Location location, Consumer<T> consumer);

    TaskChain<T> consumeRegion(UUID worldId, int chunkX, int chunkZ, Consumer<T> consumer);

    TaskChain<T> consumeEntity(Entity entity, Consumer<T> consumer);

    TaskChain<T> consumeEntity(UUID entityId, Consumer<T> consumer);

    TaskChain<T> filter(Predicate<T> predicate);

    <U> TaskChain<U> flatMap(Function<T, TaskChain<U>> mapper);

    TaskChain<T> timeout(Duration duration);

    /**
     * Retries the chain when it fails using {@code retryPolicy}.
     *
     * <p>Retry must only be used for idempotent operations. Re-executing a non-idempotent
     * step can cause duplicated side effects.
     */
    TaskChain<T> retry(RetryPolicy retryPolicy);

    TaskChain<T> onStart(Runnable action);

    TaskChain<T> onComplete(Runnable action);

    TaskChain<T> onCancel(Runnable action);

    TaskChain<T> onError(Consumer<Throwable> consumer);

    boolean cancel();

    CompletionStage<T> toCompletionStage();
}
