package com.cotani.task.api;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
            return new com.cotani.task.impl.chain.DefaultTaskChain<>(
                    CompletableFuture.completedFuture(List.of()), scheduler);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        CompletableFuture<T>[] futures = new CompletableFuture[chains.length];

        for (int index = 0; index < chains.length; index++) {
            futures[index] = chains[index].future();
        }

        CompletableFuture<List<T>> all = CompletableFuture.allOf(futures).thenApply(ignored -> {
            List<T> result = new java.util.ArrayList<>(futures.length);

            for (CompletableFuture<T> future : futures) {
                result.add(future.join());
            }

            return result;
        });

        return new com.cotani.task.impl.chain.DefaultTaskChain<>(all, scheduler);
    }

    @SafeVarargs
    @SuppressWarnings({"varargs", "unchecked"})
    static <T> TaskChain<T> anyOf(PaperTaskScheduler scheduler, TaskChain<T>... chains) {
        if (chains.length == 0) {
            throw new IllegalArgumentException("chains must not be empty");
        }

        CompletableFuture<Object> any = CompletableFuture.anyOf(
                java.util.Arrays.stream(chains).map(TaskChain::future).toArray(CompletableFuture[]::new));

        CompletableFuture<T> typed = any.thenApply(value -> (T) value);

        return new com.cotani.task.impl.chain.DefaultTaskChain<>(typed, scheduler);
    }

    <U> TaskChain<U> thenAsync(Function<T, U> function);

    <U> TaskChain<U> thenGlobal(Function<T, U> function);

    <U> TaskChain<U> thenRegion(Location location, Function<T, U> function);

    <U> TaskChain<U> thenEntity(Entity entity, Function<T, U> function);

    TaskChain<T> consumeAsync(Consumer<T> consumer);

    TaskChain<T> consumeGlobal(Consumer<T> consumer);

    TaskChain<T> consumeRegion(Location location, Consumer<T> consumer);

    TaskChain<T> consumeEntity(Entity entity, Consumer<T> consumer);

    TaskChain<T> filter(Predicate<T> predicate);

    <U> TaskChain<U> flatMap(Function<T, TaskChain<U>> mapper);

    TaskChain<T> timeout(Duration duration);

    TaskChain<T> retry(RetryPolicy retryPolicy);

    TaskChain<T> onStart(Runnable action);

    TaskChain<T> onComplete(Runnable action);

    TaskChain<T> onCancel(Runnable action);

    TaskChain<T> onError(Consumer<Throwable> consumer);

    boolean cancel();

    CompletableFuture<T> future();
}
