package com.cotani.cache.internal.caffeine;

import com.cotani.cache.policy.CacheSettings;
import com.cotani.task.api.DelayedTaskScheduler;
import com.cotani.task.api.SchedulerTask;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

final class CacheAutosaveCoordinator {
    private static final Logger LOGGER = Logger.getLogger(CacheAutosaveCoordinator.class.getName());

    private final Supplier<CompletionStage<Void>> autosaveOperation;
    private final Object lifecycleLock = new Object();
    private final SchedulerTask scheduledTask;
    private boolean cancelled;
    private boolean autosaveInProgress;
    private CompletableFuture<Void> idle = CompletableFuture.completedFuture(null);

    CacheAutosaveCoordinator(
            DelayedTaskScheduler scheduler, CacheSettings settings, Supplier<CompletionStage<Void>> autosaveOperation) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(settings, "settings");

        this.autosaveOperation = Objects.requireNonNull(autosaveOperation, "autosaveOperation");
        this.scheduledTask = settings.autosaveEnabled()
                ? scheduler.asyncTimer(this::run, settings.autosaveInterval(), settings.autosaveInterval())
                : SchedulerTask.noop();
    }

    CompletionStage<Void> cancelAndAwait() {
        final CompletableFuture<Void> currentIdle;
        synchronized (lifecycleLock) {
            cancelled = true;
            currentIdle = idle;
        }
        scheduledTask.cancel();

        return currentIdle;
    }

    private void run() {
        final CompletableFuture<Void> cycleIdle;
        synchronized (lifecycleLock) {
            if (cancelled || autosaveInProgress) {
                return;
            }
            autosaveInProgress = true;
            cycleIdle = new CompletableFuture<>();
            idle = cycleIdle;
        }

        final CompletionStage<Void> operation;
        try {
            operation = Objects.requireNonNull(autosaveOperation.get(), "autosave operation returned null");
        } catch (Throwable failure) {
            finish(cycleIdle);
            LOGGER.log(Level.SEVERE, "Could not start cache autosave", failure);
            return;
        }

        var _ = operation.whenComplete((_, error) -> {
            finish(cycleIdle);
            if (error != null) {
                LOGGER.log(Level.SEVERE, "Could not auto-save dirty cache entries", error);
            }
        });
    }

    private void finish(CompletableFuture<Void> cycleIdle) {
        synchronized (lifecycleLock) {
            autosaveInProgress = false;
        }
        cycleIdle.complete(null);
    }
}
