package com.cotani.cooldown.internal;

import com.cotani.cooldown.api.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class DefaultCooldownOperation implements CooldownOperation {

    private final CooldownStore store;
    private final Clock clock;
    private final CooldownTarget target;

    private @Nullable CooldownAction action;
    private @Nullable Duration duration;

    public DefaultCooldownOperation(CooldownStore store, Clock clock, CooldownTarget target) {
        this.store = Objects.requireNonNull(store, "store cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.target = Objects.requireNonNull(target, "target cannot be null");
    }

    @Override
    public CooldownOperation action(String action) {
        return action(CooldownAction.of(action));
    }

    @Override
    public CooldownOperation action(CooldownAction action) {
        this.action = Objects.requireNonNull(action, "action cannot be null");
        return this;
    }

    @Override
    public CooldownOperation duration(Duration duration) {
        validateDuration(duration);
        this.duration = duration;
        return this;
    }

    @Override
    public CooldownOperation policy(CooldownPolicy policy) {
        Objects.requireNonNull(policy, "policy cannot be null");
        return duration(policy.duration());
    }

    @Override
    public CooldownResult check() {
        CooldownKey key = key();
        Optional<CooldownEntry> optionalEntry = store.find(key);

        if (optionalEntry.isEmpty()) {
            return CooldownResult.allowed(key);
        }

        CooldownEntry entry = optionalEntry.get();

        if (entry.expired(clock)) {
            store.remove(key);
            return CooldownResult.allowed(key);
        }

        return CooldownResult.denied(key, entry.remaining(clock), entry.expiresAt());
    }

    @Override
    public CooldownResult start() {
        ensureDuration();

        CooldownResult result = check();

        if (result.denied()) {
            return result;
        }

        return restart();
    }

    @Override
    public CooldownResult restart() {
        ensureDuration();

        CooldownKey key = key();
        Instant startedAt = clock.instant();
        Instant expiresAt = startedAt.plus(Objects.requireNonNull(duration));

        store.save(new CooldownEntry(key, startedAt, expiresAt));

        return CooldownResult.allowed(key);
    }

    @Override
    public CooldownResult checkAndStart() {
        ensureDuration();

        return store.checkAndStart(key(), Objects.requireNonNull(duration), clock);
    }

    @Override
    public Optional<Duration> remaining() {
        CooldownResult result = check();

        if (result.allowed()) {
            return Optional.empty();
        }

        return Optional.of(result.remaining());
    }

    @Override
    public boolean active() {
        return check().denied();
    }

    @Override
    public void remove() {
        store.remove(key());
    }

    private CooldownKey key() {
        ensureAction();
        return new CooldownKey(target, Objects.requireNonNull(action));
    }

    private void ensureAction() {
        if (action != null) {
            return;
        }

        throw new IllegalStateException("cooldown action was not defined");
    }

    private void ensureDuration() {
        if (duration != null) {
            return;
        }

        throw new IllegalStateException("cooldown duration was not defined");
    }

    private void validateDuration(Duration duration) {
        Objects.requireNonNull(duration, "duration cannot be null");

        if (duration.isPositive()) {
            return;
        }

        throw new IllegalArgumentException("duration must be positive");
    }
}
