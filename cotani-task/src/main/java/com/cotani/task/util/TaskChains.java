package com.cotani.task.util;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.TaskChain;

/**
 * Factory methods for commonly used {@link TaskChain} instances.
 */
public final class TaskChains {

    private TaskChains() {}

    /**
     * Returns a completed task chain with no value.
     *
     * <p>Use this instead of {@code scheduler.supplyAsync(() -> null)} to avoid explicit
     * {@code null} returns in {@code @NullMarked} packages.
     */
    public static TaskChain<Void> completedVoid(PaperTaskScheduler scheduler) {
        return scheduler.chain(CompletionStages.completedVoid());
    }
}
