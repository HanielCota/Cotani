package com.cotani.task.throttle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class TokenBucketRateLimiter implements RateLimiter {

    private static final long NANOTOKENS_PER_TOKEN = 1_000_000_000L;

    private final long capacityNanotokens;
    private final long refillTokensNanotokens;
    private final long refillPeriodNanos;
    private final AtomicReference<State> state;

    public TokenBucketRateLimiter(long capacity, Duration refillPeriod) {
        this(capacity, capacity, refillPeriod);
    }

    public TokenBucketRateLimiter(long capacity, long refillTokens, Duration refillPeriod) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }

        if (refillTokens <= 0) {
            throw new IllegalArgumentException("refillTokens must be positive");
        }

        Objects.requireNonNull(refillPeriod, "refillPeriod");

        if (refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("refillPeriod must be positive");
        }

        this.capacityNanotokens = capacity * NANOTOKENS_PER_TOKEN;
        this.refillTokensNanotokens = refillTokens * NANOTOKENS_PER_TOKEN;
        this.refillPeriodNanos = refillPeriod.toNanos();
        this.state = new AtomicReference<>(new State(capacityNanotokens, System.nanoTime()));
    }

    @Override
    @SuppressWarnings({"removal", "BusyWait"})
    public void acquire() throws InterruptedException {
        while (!tryAcquire()) {
            long sleepMillis = Math.max(1, refillPeriodNanos / 1_000_000);
            Thread.sleep(sleepMillis);
        }
    }

    @Override
    public boolean tryAcquire() {
        long now = System.nanoTime();

        while (true) {
            State current = Objects.requireNonNull(state.get(), "state");
            State next = refilled(current, now);

            if (next.nanotokens < NANOTOKENS_PER_TOKEN) {
                state.compareAndSet(current, next);

                return false;
            }

            State afterAcquire = new State(next.nanotokens - NANOTOKENS_PER_TOKEN, next.lastRefillNanos);

            if (state.compareAndSet(current, afterAcquire)) {
                return true;
            }
        }
    }

    @Override
    @SuppressWarnings({"removal", "BusyWait"})
    public boolean tryAcquire(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");

        long deadline = System.nanoTime() + timeout.toNanos();
        long sleepMillis = Math.max(1, refillPeriodNanos / 1_000_000);

        while (System.nanoTime() < deadline) {
            if (tryAcquire()) {
                return true;
            }

            Thread.sleep(sleepMillis);
        }

        return false;
    }

    @Override
    public Duration retryDelay() {
        long now = System.nanoTime();
        State current = Objects.requireNonNull(state.get(), "state");
        State refilled = refilled(current, now);

        if (refilled.nanotokens >= NANOTOKENS_PER_TOKEN) {
            return Duration.ZERO;
        }

        long nextRefillNanos = refilled.lastRefillNanos + refillPeriodNanos;
        long remaining = nextRefillNanos - now;
        return remaining > 0 ? Duration.ofNanos(remaining) : Duration.ZERO;
    }

    private State refilled(State current, long now) {
        long elapsed = now - current.lastRefillNanos;
        long periods = elapsed / refillPeriodNanos;

        if (periods == 0) {
            return current;
        }

        long added = periods * refillTokensNanotokens;
        long newTokens = Math.min(capacityNanotokens, current.nanotokens + added);
        long newLastRefill = current.lastRefillNanos + periods * refillPeriodNanos;

        return new State(newTokens, newLastRefill);
    }

    private record State(long nanotokens, long lastRefillNanos) {}
}
