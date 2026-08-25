package com.cotani.cleanup.paper;

import com.cotani.cleanup.api.CleanupPolicy;
import com.cotani.cleanup.api.CleanupService;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Schedules bounded recurring cleanup runs without owning the plugin scheduler. */
public final class PaperCleanupScheduler {
    private static final Logger LOGGER = Logger.getLogger(PaperCleanupScheduler.class.getName());

    private final PaperTaskScheduler scheduler;
    private final CleanupService cleanupService;

    public PaperCleanupScheduler(PaperTaskScheduler scheduler, CleanupService cleanupService) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.cleanupService = Objects.requireNonNull(cleanupService, "cleanupService");
    }

    public CleanupSchedule schedule(CleanupPolicy policy, Duration initialDelay, Duration period, String reason) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(reason, "reason");
        if (initialDelay.isNegative() || period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("initialDelay must not be negative and period must be positive");
        }
        var task = scheduler.globalTimer(
                () -> cleanupService
                        .executeAsync(cleanupService.newRequest(policy, reason))
                        .whenComplete((ignored, failure) -> {
                            if (failure != null) {
                                LOGGER.log(Level.WARNING, "Scheduled cleanup failed", failure);
                            }
                        }),
                initialDelay,
                period);
        return new CleanupSchedule(task);
    }
}
