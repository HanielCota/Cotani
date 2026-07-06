package com.cotani.cooldown;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public final class InMemoryCooldownStore implements CooldownStore {

    private final ConcurrentMap<CooldownKey, CooldownEntry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<CooldownEntry> find(CooldownKey key) {
        Objects.requireNonNull(key, "key cannot be null");

        return Optional.ofNullable(entries.get(key));
    }

    @Override
    public void save(CooldownEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");

        entries.put(entry.key(), entry);
    }

    @Override
    public void remove(CooldownKey key) {
        Objects.requireNonNull(key, "key cannot be null");

        entries.remove(key);
    }

    @Override
    public void removeExpired(Clock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");

        entries.entrySet().removeIf(entry -> entry.getValue().expired(clock));
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public CooldownResult checkAndStart(CooldownKey key, Duration duration, Clock clock) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(duration, "duration cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");

        Instant now = clock.instant();
        AtomicReference<@Nullable CooldownResult> resultReference = new AtomicReference<>();

        entries.compute(key, (ignored, current) -> {
            if (current != null && !current.expired(clock)) {
                resultReference.set(CooldownResult.denied(key, current.remaining(clock), current.expiresAt()));

                return current;
            }

            Instant expiresAt = now.plus(duration);
            CooldownEntry created = new CooldownEntry(key, now, expiresAt);

            resultReference.set(CooldownResult.allowed(key));

            return created;
        });

        return Objects.requireNonNull(resultReference.get());
    }
}
