package com.cotani.job;

import com.cotani.job.api.JobService;
import com.cotani.job.api.JobServiceOptions;
import com.cotani.job.internal.DefaultJobService;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.persistence.NoopPersistentTaskStore;
import com.cotani.task.persistence.PersistentTaskStore;
import java.util.Objects;

/** Factories for the {@code cotani-job} module. */
public final class CotaniJobs {
    private CotaniJobs() {}

    /** Creates a service backed by the caller-owned persistent task store. */
    public static JobService create(PaperTaskScheduler scheduler, PersistentTaskStore store) {
        return create(scheduler, store, JobServiceOptions.defaults());
    }

    /** Creates a service with explicit runtime policies and persistence. */
    public static JobService create(
            PaperTaskScheduler scheduler, PersistentTaskStore store, JobServiceOptions options) {
        return DefaultJobService.create(
                Objects.requireNonNull(scheduler, "scheduler"),
                Objects.requireNonNull(store, "store"),
                Objects.requireNonNull(options, "options"));
    }

    /** Creates a non-durable service intended for tests and ephemeral jobs. */
    public static JobService inMemory(PaperTaskScheduler scheduler) {
        return create(scheduler, new NoopPersistentTaskStore());
    }
}
