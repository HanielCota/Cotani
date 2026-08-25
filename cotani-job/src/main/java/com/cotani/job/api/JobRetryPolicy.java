package com.cotani.job.api;

import java.time.Duration;
import java.util.Objects;

/** Bounded exponential backoff policy for failed job attempts. */
public record JobRetryPolicy(int maxAttempts, Duration initialBackoff, Duration maximumBackoff, double multiplier) {
    public JobRetryPolicy {
        Objects.requireNonNull(initialBackoff, "initialBackoff");
        Objects.requireNonNull(maximumBackoff, "maximumBackoff");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        if (initialBackoff.isNegative()) {
            throw new IllegalArgumentException("initialBackoff must not be negative");
        }
        if (maximumBackoff.isNegative() || maximumBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maximumBackoff must not be less than initialBackoff");
        }
        if (!Double.isFinite(multiplier) || multiplier < 1.0d) {
            throw new IllegalArgumentException("multiplier must be finite and at least 1.0");
        }
        nanos(initialBackoff, "initialBackoff");
        nanos(maximumBackoff, "maximumBackoff");
    }

    public static JobRetryPolicy defaults() {
        return new JobRetryPolicy(3, Duration.ofSeconds(1), Duration.ofMinutes(5), 2.0d);
    }

    /** Returns the delay before the next attempt after {@code completedAttempt}. */
    public Duration delayBeforeNextAttempt(int completedAttempt) {
        if (completedAttempt <= 0 || completedAttempt >= maxAttempts) {
            throw new IllegalArgumentException("completedAttempt must be between 1 and maxAttempts - 1");
        }

        var delay = nanos(initialBackoff, "initialBackoff");
        var maximumDelay = nanos(maximumBackoff, "maximumBackoff");
        for (int attempt = 1; attempt < completedAttempt; attempt++) {
            if (delay >= maximumDelay) {
                return maximumBackoff;
            }
            var next = (double) delay * multiplier;
            if (next >= maximumDelay) {
                return maximumBackoff;
            }
            delay = Math.max(delay, (long) next);
        }
        return Duration.ofNanos(Math.min(delay, maximumDelay));
    }

    private static long nanos(Duration duration, String name) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException(name + " is too large", overflow);
        }
    }
}
