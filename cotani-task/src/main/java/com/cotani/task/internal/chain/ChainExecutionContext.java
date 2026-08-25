package com.cotani.task.internal.chain;

import com.cotani.task.api.AsyncTaskExecutor;
import com.cotani.task.api.DelayedTaskScheduler;
import java.util.Objects;

record ChainExecutionContext(AsyncTaskExecutor executor, DelayedTaskScheduler delays) {
    ChainExecutionContext {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(delays, "delays");
    }
}
