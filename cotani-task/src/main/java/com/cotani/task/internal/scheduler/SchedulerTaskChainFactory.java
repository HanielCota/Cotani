package com.cotani.task.internal.scheduler;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;
import com.cotani.task.internal.chain.DefaultTaskChain;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

final class SchedulerTaskChainFactory {
    private final PaperTaskScheduler scheduler;
    private final TargetTaskDispatcher dispatcher;

    SchedulerTaskChainFactory(PaperTaskScheduler scheduler, TargetTaskDispatcher dispatcher) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    <T> TaskChain<T> supplyAsync(String name, Supplier<T> supplier) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(supplier, "supplier");

        Supplier<CompletableFuture<T>> factory = () -> dispatcher.supply(ExecutionTarget.async(), name, supplier);

        return DefaultTaskChain.create(factory.get(), scheduler, factory);
    }

    <T> TaskChain<T> chain(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");

        return DefaultTaskChain.create(stage.toCompletableFuture(), scheduler);
    }
}
