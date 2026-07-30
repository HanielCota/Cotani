package com.cotani.task.api;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Creates repeatable scheduler chains or adapts an external, non-repeatable stage. */
public interface TaskChainFactory {

    <T> TaskChain<T> supplyAsync(Supplier<T> supplier);

    <T> TaskChain<T> supplyAsync(String name, Supplier<T> supplier);

    /**
     * Adapts an external stage. The returned chain is not repeatable and therefore rejects retry.
     */
    <T> TaskChain<T> chain(CompletionStage<T> stage);
}
