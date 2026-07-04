package com.cotani.task.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Factory methods for commonly used {@link CompletionStage} instances.
 */
public final class CompletionStages {

    private CompletionStages() {}

    /**
     * Returns a completed stage with no value.
     *
     * <p>Use this instead of {@code CompletableFuture.completedFuture(null)} to avoid explicit
     * {@code null} returns in {@code @NullMarked} packages.
     */
    @SuppressWarnings("NullAway")
    public static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }
}
