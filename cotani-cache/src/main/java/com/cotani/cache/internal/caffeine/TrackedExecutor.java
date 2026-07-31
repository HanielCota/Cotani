package com.cotani.cache.internal.caffeine;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

final class TrackedExecutor implements Executor {
    private final Executor delegate;
    private final Object lock = new Object();
    private int activeTasks;
    private CompletableFuture<Void> idle = CompletableFuture.completedFuture(null);

    TrackedExecutor(Executor delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");

        var started = new AtomicBoolean();
        synchronized (lock) {
            if (activeTasks == 0) {
                idle = new CompletableFuture<>();
            }
            activeTasks++;
        }
        try {
            delegate.execute(() -> {
                started.set(true);
                try {
                    command.run();
                } finally {
                    taskFinished();
                }
            });
        } catch (RuntimeException schedulingFailure) {
            // A direct executor propagates task failures from execute(...) after the wrapper's
            // finally block has already updated the count. Only rejected, never-started work
            // still needs to be removed here.
            if (!started.get()) {
                taskFinished();
            }
            throw schedulingFailure;
        }
    }

    CompletionStage<Void> whenIdle() {
        synchronized (lock) {
            return idle;
        }
    }

    private void taskFinished() {
        CompletableFuture<Void> completed = null;
        synchronized (lock) {
            activeTasks--;
            if (activeTasks == 0) {
                completed = idle;
            }
        }
        if (completed != null) {
            completed.complete(null);
        }
    }
}
