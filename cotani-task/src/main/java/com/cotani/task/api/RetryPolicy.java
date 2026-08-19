package com.cotani.task.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class RetryPolicy {
    private static final double MIN_JITTER = 0.0;
    private static final double MAX_JITTER = 1.0;

    private final int maxAttempts;
    private final Duration baseDelay;
    private final double multiplier;
    private final double jitter;
    private final boolean symmetricJitter;
    private final Predicate<Throwable> retryable;

    private RetryPolicy(
            int maxAttempts,
            Duration baseDelay,
            double multiplier,
            double jitter,
            Predicate<Throwable> retryable,
            boolean symmetricJitter) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }

        if (multiplier <= 0) {
            throw new IllegalArgumentException("multiplier must be positive");
        }

        this.maxAttempts = maxAttempts;
        this.baseDelay = Objects.requireNonNull(baseDelay, "baseDelay");

        if (baseDelay.isNegative()) {
            throw new IllegalArgumentException("baseDelay must not be negative");
        }

        this.multiplier = multiplier;
        this.jitter = validateJitter(jitter);
        this.symmetricJitter = symmetricJitter;
        this.retryable = Objects.requireNonNull(retryable, "retryable");
    }

    public static RetryPolicy create(
            int maxAttempts,
            Duration baseDelay,
            double multiplier,
            double jitter,
            Predicate<Throwable> retryable,
            boolean symmetricJitter) {
        return new RetryPolicy(maxAttempts, baseDelay, multiplier, jitter, retryable, symmetricJitter);
    }

    public static RetryPolicy fixed(int maxAttempts, Duration baseDelay) {
        return RetryPolicy.Builder.create(maxAttempts, baseDelay).build();
    }

    public static RetryPolicy exponential(int maxAttempts, Duration baseDelay) {
        return RetryPolicy.Builder.create(maxAttempts, baseDelay).exponential().build();
    }

    public static RetryPolicy exponentialWithJitter(int maxAttempts, Duration baseDelay) {
        return exponentialWithJitter(maxAttempts, baseDelay, 0.2);
    }

    public static RetryPolicy exponentialWithJitter(int maxAttempts, Duration baseDelay, double jitter) {
        return new RetryPolicy.Builder(maxAttempts, baseDelay)
                .exponential()
                .withJitter(jitter)
                .build();
    }

    private static double validateJitter(double jitter) {
        if (jitter < MIN_JITTER || jitter > MAX_JITTER) {
            throw new IllegalArgumentException("Jitter must be between " + MIN_JITTER + " and " + MAX_JITTER);
        }

        return jitter;
    }

    public boolean shouldRetry(int attempt, Throwable throwable) {
        if (attempt >= maxAttempts) {
            return false;
        }

        return retryable.test(throwable);
    }

    public Duration delayFor(int attempt) {
        return Duration.ofMillis(delayMillisForAttempt(attempt));
    }

    public long delayMillisForAttempt(int attempt) {
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }

        long baseMillis = baseDelay.toMillis();
        double calculated = baseMillis * Math.pow(multiplier, attempt - 1.0);

        if (!Double.isFinite(calculated) || calculated > Long.MAX_VALUE) {
            throw new IllegalArgumentException("retry delay is too large for attempt " + attempt);
        }

        long exponential = (long) calculated;

        if (jitter == MIN_JITTER) {
            return Math.max(0, exponential);
        }

        double factor = symmetricJitter
                ? 1 + jitter * (ThreadLocalRandom.current().nextDouble() - 0.5) * 2
                : 1 - jitter * ThreadLocalRandom.current().nextDouble();

        return Math.max(0, (long) (exponential * factor));
    }

    public RetryPolicy retryIf(Predicate<Throwable> predicate) {
        Objects.requireNonNull(predicate, "predicate");

        return RetryPolicy.create(maxAttempts, baseDelay, multiplier, jitter, predicate, symmetricJitter);
    }

    public RetryPolicy withJitter(double jitter) {
        return RetryPolicy.create(
                maxAttempts, baseDelay, multiplier, validateJitter(jitter), retryable, symmetricJitter);
    }

    public RetryPolicy withSymmetricJitter() {
        return RetryPolicy.create(maxAttempts, baseDelay, multiplier, jitter, retryable, true);
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public Duration delay() {
        return baseDelay;
    }

    public double backoffMultiplier() {
        return multiplier;
    }

    public double jitterFactor() {
        return jitter;
    }

    public boolean symmetricJitter() {
        return symmetricJitter;
    }

    public Predicate<Throwable> retryIf() {
        return retryable;
    }

    public static final class Builder {
        private final int maxAttempts;
        private final Duration baseDelay;
        private double multiplier = 1.0;
        private double jitter = MIN_JITTER;
        private boolean symmetricJitter;
        private Predicate<Throwable> retryable = error -> true;

        private Builder(int maxAttempts, Duration baseDelay) {
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("maxAttempts must be positive");
            }

            this.maxAttempts = maxAttempts;
            this.baseDelay = Objects.requireNonNull(baseDelay, "baseDelay");
        }

        public static Builder create(int maxAttempts, Duration baseDelay) {
            return new Builder(maxAttempts, baseDelay);
        }

        public Builder exponential() {
            this.multiplier = 2.0;

            return this;
        }

        public Builder withMultiplier(double multiplier) {
            if (multiplier <= 0) {
                throw new IllegalArgumentException("multiplier must be positive");
            }

            this.multiplier = multiplier;

            return this;
        }

        public Builder withJitter(double jitter) {
            this.jitter = validateJitter(jitter);

            return this;
        }

        public Builder withSymmetricJitter() {
            this.symmetricJitter = true;

            return this;
        }

        public Builder retryIf(Predicate<Throwable> retryable) {
            this.retryable = Objects.requireNonNull(retryable, "retryable");

            return this;
        }

        public RetryPolicy build() {
            return RetryPolicy.create(maxAttempts, baseDelay, multiplier, jitter, retryable, symmetricJitter);
        }
    }
}
