package com.cotani.testkit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;

/** Deterministic iteration and bounded concurrency helpers for stress tests. */
public final class StressTestSupport {
    public static final int MINIMUM_ITERATIONS = 1_000;
    private static final int DEFAULT_ITERATIONS = 1_200;
    private static final long DEFAULT_SEED = 6_840_227_782_638_526_189L;

    private StressTestSupport() {}

    public static int iterations() {
        return Math.max(MINIMUM_ITERATIONS, Integer.getInteger("cotani.test.iterations", DEFAULT_ITERATIONS));
    }

    public static long rootSeed() {
        return Long.getLong("cotani.test.seed", DEFAULT_SEED);
    }

    public static void scenarios(String module, String operation, Scenario scenario) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(scenario, "scenario");
        long seed = rootSeed();
        for (int iteration = 0; iteration < iterations(); iteration++) {
            var random = SeededRandom.scenario(seed, module, operation, iteration);
            var player = PlayerScenarioFactory.player(random, iteration);
            var context = new ScenarioContext(seed, iteration, module, operation, player.id());
            context.verify(() -> scenario.run(context, random, player));
        }
    }

    public static <T> List<T> concurrent(
            String module,
            String operation,
            int operationCount,
            int parallelism,
            Duration timeout,
            IntFunction<? extends CompletionStage<T>> action) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(action, "action");
        if (operationCount < 1 || parallelism < 1) {
            throw new IllegalArgumentException("operationCount and parallelism must be positive");
        }

        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        var start = new CyclicBarrier(Math.min(operationCount, parallelism));
        try {
            var results = new ArrayList<CompletableFuture<T>>(operationCount);
            for (int index = 0; index < operationCount; index++) {
                int operationIndex = index;
                var result = CompletableFuture.supplyAsync(
                                () -> {
                                    if (operationIndex < parallelism) {
                                        awaitBarrier(start, timeout);
                                    }
                                    return Objects.requireNonNull(action.apply(operationIndex), "action stage");
                                },
                                executor)
                        .thenCompose(CompletionStage::toCompletableFuture);
                results.add(result);
            }
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new))
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            var values = new ArrayList<T>(operationCount);
            for (var result : results) {
                values.add(result.getNow(null));
            }
            return Collections.unmodifiableList(values);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(context(module, operation, operationCount) + ": interrupted", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new AssertionError(
                    context(module, operation, operationCount) + ": concurrent execution failed", failure);
        } finally {
            executor.shutdownNow();
            awaitTermination(executor, module, operation, operationCount);
        }
    }

    public static <T> T await(CompletionStage<T> stage, Duration timeout, ScenarioContext context) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(context, "context");
        try {
            return stage.toCompletableFuture().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(context.description() + ": interrupted", failure);
        } catch (ExecutionException | TimeoutException failure) {
            throw new AssertionError(context.description() + ": asynchronous operation failed", failure);
        }
    }

    private static void awaitBarrier(CyclicBarrier barrier, Duration timeout) {
        try {
            barrier.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("concurrency barrier interrupted", failure);
        } catch (java.util.concurrent.BrokenBarrierException | TimeoutException failure) {
            throw new IllegalStateException("concurrency barrier failed", failure);
        }
    }

    private static void awaitTermination(
            ExecutorService executor, String module, String operation, int operationCount) {
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError(context(module, operation, operationCount) + ": executor did not terminate");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    context(module, operation, operationCount) + ": interrupted while terminating executor", failure);
        }
    }

    private static String context(String module, String operation, int operationCount) {
        return "seed=" + rootSeed() + ", module=" + module + ", operation=" + operation + ", operations="
                + operationCount;
    }

    @FunctionalInterface
    public interface Scenario {
        void run(ScenarioContext context, SeededRandom random, TestPlayer player) throws Exception;
    }
}
