package com.cotani.cooldown.internal;

import com.cotani.api.InternalApi;
import com.cotani.cooldown.api.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class InMemoryCooldownStore implements CooldownStore {

    private static final String KEY_NULL_MSG = "key cannot be null";

    private final ConcurrentMap<CooldownKey, CooldownEntry> entries = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupEpochMilli = new AtomicLong(Long.MIN_VALUE);

    @Override
    public Optional<CooldownEntry> find(CooldownKey key) {
        Objects.requireNonNull(key, KEY_NULL_MSG);

        return Optional.ofNullable(entries.get(key));
    }

    @Override
    public void save(CooldownEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");

        entries.put(entry.key(), entry);
    }

    @Override
    public void remove(CooldownKey key) {
        Objects.requireNonNull(key, KEY_NULL_MSG);

        entries.remove(key);
    }

    @Override
    public void removeExpired(Clock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");

        Instant now = clock.instant();
        entries.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public CooldownResult checkAndStart(CooldownKey key, Duration duration, Clock clock) {
        Objects.requireNonNull(key, KEY_NULL_MSG);
        Objects.requireNonNull(duration, "duration cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        if (!duration.isPositive()) {
            throw new IllegalArgumentException("duration must be positive");
        }

        Instant now = clock.instant();
        cleanupWhenDue(now);
        AtomicReference<@Nullable CooldownResult> resultReference = new AtomicReference<>();

        entries.compute(key, (ignored, current) -> {
            if (current != null && !current.expired(now)) {
                resultReference.set(CooldownResult.denied(key, current.remaining(now), current.expiresAt()));

                return current;
            }

            Instant expiresAt = now.plus(duration);
            CooldownEntry created = new CooldownEntry(key, now, expiresAt);

            resultReference.set(CooldownResult.allowed(key));

            return created;
        });

        return Objects.requireNonNull(resultReference.get());
    }

    public long estimatedSize() {
        return entries.size();
    }

    private void cleanupWhenDue(Instant now) {
        long nowMillis = now.toEpochMilli();
        long nextCleanup = nextCleanupEpochMilli.get();
        if (nowMillis < nextCleanup || !nextCleanupEpochMilli.compareAndSet(nextCleanup, safeNextCleanup(now))) {
            return;
        }
        entries.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    private static long safeNextCleanup(Instant now) {
        try {
            return now.plus(Duration.ofMinutes(1)).toEpochMilli();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
