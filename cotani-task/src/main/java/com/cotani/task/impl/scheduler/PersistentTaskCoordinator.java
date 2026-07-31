package com.cotani.task.impl.scheduler;

import com.cotani.task.api.SchedulerTask;
import com.cotani.task.impl.task.LazySchedulerTask;
import com.cotani.task.persistence.PersistentTask;
import com.cotani.task.persistence.PersistentTaskStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class PersistentTaskCoordinator {
    private final PersistentTaskStore store;
    private final NamedAsyncTaskScheduler scheduler;
    private final Clock clock;

    PersistentTaskCoordinator(PersistentTaskStore store, NamedAsyncTaskScheduler scheduler) {
        this(store, scheduler, Clock.systemUTC());
    }

    PersistentTaskCoordinator(PersistentTaskStore store, NamedAsyncTaskScheduler scheduler, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    SchedulerTask persistAndRun(String name, Duration delay, byte[] payload, Consumer<byte[]> executor) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(executor, "executor");

        var task = new PersistentTask(UUID.randomUUID(), name, Instant.now(clock), delay, payload);
        var lazyTask = new LazySchedulerTask();
        var persistentHandle = new PersistentSchedulerTask(task, lazyTask);

        SchedulerTask saveTask = scheduler.execute("persist-save-" + name, () -> {
            final SchedulerTask executionTask;
            try {
                store.save(task);
            } catch (Throwable failure) {
                lazyTask.failSetup(failure);
                return;
            }
            persistentHandle.markPersisted();

            if (lazyTask.cancelled()) {
                persistentHandle.completePersistence();
                lazyTask.completeSetup(SchedulerTask.noop());
                return;
            }

            executionTask = scheduler.schedule(
                    "persist-run-" + name,
                    () -> {
                        try {
                            executor.accept(task.payload());
                        } finally {
                            persistentHandle.completePersistence();
                        }
                    },
                    delay);
            lazyTask.setDelegate(executionTask);
            lazyTask.completeSetup(executionTask);
        });

        lazyTask.setSetupTask(saveTask);

        return persistentHandle;
    }

    List<PersistentTask> loadPending() {
        return store.loadPending();
    }

    private final class PersistentSchedulerTask implements SchedulerTask {
        private final PersistentTask persistentTask;
        private final LazySchedulerTask delegate;
        private final AtomicBoolean persisted = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean completionScheduled = new AtomicBoolean();
        private boolean persistenceCompleted;

        private PersistentSchedulerTask(PersistentTask persistentTask, LazySchedulerTask delegate) {
            this.persistentTask = persistentTask;
            this.delegate = delegate;
        }

        private void markPersisted() {
            persisted.set(true);
        }

        private synchronized void completePersistence() {
            if (persistenceCompleted) {
                return;
            }
            store.markCompleted(persistentTask);
            persistenceCompleted = true;
        }

        private void scheduleCancellationPersistence() {
            if (!persisted.get() || !completionScheduled.compareAndSet(false, true)) {
                return;
            }
            try {
                scheduler.execute("persist-cancel-" + persistentTask.taskName(), () -> {
                    try {
                        completePersistence();
                    } finally {
                        completionScheduled.set(false);
                    }
                });
            } catch (RuntimeException schedulingFailure) {
                completionScheduled.set(false);
                throw schedulingFailure;
            }
        }

        @Override
        public boolean cancel() {
            boolean changed = cancelled.compareAndSet(false, true);
            delegate.cancel();
            scheduleCancellationPersistence();

            return changed;
        }

        @Override
        public boolean cancelled() {
            return cancelled.get() || delegate.cancelled();
        }
    }
}
