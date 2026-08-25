package com.cotani.cleanup;

import com.cotani.cleanup.api.CleanupExecutor;
import com.cotani.cleanup.api.CleanupProtection;
import com.cotani.cleanup.api.CleanupService;
import com.cotani.cleanup.api.CleanupServiceOptions;
import com.cotani.cleanup.internal.DefaultCleanupService;
import com.cotani.cleanup.internal.InMemoryCleanupExecutor;
import com.cotani.cleanup.paper.PaperCleanupExecutor;
import com.cotani.cleanup.paper.PaperCleanupScheduler;
import com.cotani.event.api.EventBus;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Clock;
import java.util.Objects;

/** Factories for the {@code cotani-cleanup} module. */
public final class CotaniCleanups {
    private CotaniCleanups() {}

    /** Creates a deterministic in-memory service for tests and dry-run tooling. */
    public static CleanupService inMemory(EventBus eventBus) {
        return fromExecutor(new InMemoryCleanupExecutor(), eventBus);
    }

    /** Creates the standard Paper/Folia adapter over the caller-owned scheduler. */
    public static CleanupService paper(PaperTaskScheduler scheduler, EventBus eventBus) {
        return fromExecutor(new PaperCleanupExecutor(scheduler), eventBus);
    }

    /** Creates the Paper/Folia adapter with immutable snapshot-based protection rules. */
    public static CleanupService paper(PaperTaskScheduler scheduler, EventBus eventBus, CleanupProtection protection) {
        return fromExecutor(new PaperCleanupExecutor(scheduler, protection), eventBus);
    }

    /** Creates a recurring scheduler facade over an existing cleanup service. */
    public static PaperCleanupScheduler scheduler(PaperTaskScheduler scheduler, CleanupService cleanupService) {
        return new PaperCleanupScheduler(scheduler, cleanupService);
    }

    /** Creates a service over a caller-owned Paper or test executor. */
    public static CleanupService fromExecutor(CleanupExecutor executor, EventBus eventBus) {
        return fromExecutor(executor, eventBus, CleanupServiceOptions.defaults());
    }

    /** Creates a service over an executor with explicit operational limits. */
    public static CleanupService fromExecutor(
            CleanupExecutor executor, EventBus eventBus, CleanupServiceOptions options) {
        return DefaultCleanupService.create(
                Objects.requireNonNull(executor, "executor"),
                Objects.requireNonNull(eventBus, "eventBus"),
                Objects.requireNonNull(options, "options"),
                Clock.systemUTC());
    }

    /** Creates a service with a test clock. */
    public static CleanupService fromExecutor(
            CleanupExecutor executor, EventBus eventBus, CleanupServiceOptions options, Clock clock) {
        return DefaultCleanupService.create(
                Objects.requireNonNull(executor, "executor"),
                Objects.requireNonNull(eventBus, "eventBus"),
                Objects.requireNonNull(options, "options"),
                Objects.requireNonNull(clock, "clock"));
    }
}
