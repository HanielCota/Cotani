package com.cotani.cache.entry;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

public final class CacheEntry<V> {

    private record EntryState<V>(
            V value, boolean dirty, @Nullable Instant lastSavedAt) {
        EntryState {
            Objects.requireNonNull(value, "value");
        }
    }

    private final AtomicReference<EntryState<V>> state;
    private final Instant loadedAt;

    public CacheEntry(V value) {
        this.state = new AtomicReference<>(new EntryState<>(Objects.requireNonNull(value, "value"), false, null));
        this.loadedAt = Instant.now();
    }

    public V value() {
        return currentState().value();
    }

    public synchronized V update(UnaryOperator<V> updater) {
        Objects.requireNonNull(updater, "updater");

        var current = currentState();
        var updated = Objects.requireNonNull(updater.apply(current.value()), "updated");
        state.set(new EntryState<>(updated, true, current.lastSavedAt()));
        return updated;
    }

    public synchronized V mutate(Consumer<V> mutator) {
        Objects.requireNonNull(mutator, "mutator");

        var current = currentState();
        var value = current.value();
        mutator.accept(value);
        state.set(new EntryState<>(value, true, current.lastSavedAt()));
        return value;
    }

    public synchronized boolean dirty() {
        return currentState().dirty();
    }

    public synchronized void markDirty() {
        var current = currentState();
        state.set(new EntryState<>(current.value(), true, current.lastSavedAt()));
    }

    public synchronized void markSaved() {
        var current = currentState();
        state.set(new EntryState<>(current.value(), false, Instant.now()));
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    public synchronized Optional<Instant> lastSavedAt() {
        return Optional.ofNullable(currentState().lastSavedAt());
    }

    private EntryState<V> currentState() {
        return Objects.requireNonNull(state.get(), "state");
    }
}
