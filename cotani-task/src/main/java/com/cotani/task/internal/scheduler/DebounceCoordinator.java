package com.cotani.task.internal.scheduler;

import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class DebounceCoordinator {
    private final NamedAsyncTaskScheduler scheduler;
    private final Map<String, DebounceTask> pending = new ConcurrentHashMap<>();

    DebounceCoordinator(NamedAsyncTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    SchedulerTask debounce(String name, Runnable runnable, Duration quietPeriod) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(runnable, "runnable");
        Objects.requireNonNull(quietPeriod, "quietPeriod");

        var debounce = new DebounceTask(name, runnable);
        pending.compute(name, (_, current) -> {
            if (current != null) {
                current.supersede();
            }
            return debounce;
        });

        try {
            SchedulerTask scheduled = scheduler.schedule("debounce-" + name, debounce::executeIfCurrent, quietPeriod);
            debounce.attach(scheduled);

            return debounce;
        } catch (Exception failure) {
            pending.remove(name, debounce);
            debounce.cancel();
            throw failure;
        }
    }

    void cancelAll() {
        pending.values().forEach(SchedulerTask::cancel);
        pending.clear();
    }

    private final class DebounceTask implements SchedulerTask {
        private final String name;
        private final Runnable runnable;
        private final AtomicReference<SchedulerTask> delegate = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean executed = new AtomicBoolean();

        private DebounceTask(String name, Runnable runnable) {
            this.name = name;
            this.runnable = runnable;
        }

        private void attach(SchedulerTask task) {
            delegate.set(Objects.requireNonNull(task, "task"));
            if (cancelled.get() || executed.get()) {
                task.cancel();
            }
        }

        private void executeIfCurrent() {
            if (!pending.remove(name, this) || cancelled.get() || !executed.compareAndSet(false, true)) {
                return;
            }
            runnable.run();
        }

        @Override
        public boolean cancel() {
            boolean changed = supersede();
            pending.remove(name, this);

            return changed;
        }

        private boolean supersede() {
            boolean changed = cancelled.compareAndSet(false, true);
            SchedulerTask task = delegate.get();

            if (task != null) {
                task.cancel();
            }
            return changed;
        }

        @Override
        public boolean cancelled() {
            SchedulerTask task = delegate.get();
            return cancelled.get() || (task != null && task.cancelled());
        }
    }
}
