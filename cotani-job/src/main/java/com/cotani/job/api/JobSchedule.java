package com.cotani.job.api;

import java.time.Duration;
import java.util.Objects;

/** Defines whether a job runs once or repeats after each successful execution. */
public sealed interface JobSchedule permits JobSchedule.Once, JobSchedule.Recurring {
    /** Runs the job once after {@code delay}. */
    record Once(Duration delay) implements JobSchedule {
        public Once {
            validateDuration(delay, "delay");
        }
    }

    /** Repeats the job after every successful execution. */
    record Recurring(Duration initialDelay, Duration interval) implements JobSchedule {
        public Recurring {
            validateDuration(initialDelay, "initialDelay");
            validatePositiveDuration(interval, "interval");
        }
    }

    private static void validateDuration(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void validatePositiveDuration(Duration duration, String name) {
        validateDuration(duration, name);
        if (duration.isZero()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
