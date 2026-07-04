package com.cotani.cache.entry;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * Wraps a cached value with dirty tracking and save timestamps.
 *
 * <p>All state is held in an {@link AtomicReference} for thread-safe updates.
 * Mutating operations use optimistic CAS; reads are lock-free.
 *
 * @param <V> the value type
 */
public final class CacheEntry<V> {

    private final AtomicReference<EntryState<V>> state;
    private final Instant loadedAt;

    public CacheEntry(V value) {
        this.state = new AtomicReference<>(new EntryState<>(Objects.requireNonNull(value, "value"), false, null));
        this.loadedAt = Instant.now();
    }

    public V value() {
        return currentState().value();
    }

    public V update(UnaryOperator<V> updater) {
        Objects.requireNonNull(updater, "updater");

        return state.updateAndGet(current -> {
                    var updated = Objects.requireNonNull(updater.apply(current.value()), "updated");
                    return new EntryState<>(updated, true, current.lastSavedAt());
                })
                .value();
    }

    public V mutate(Consumer<V> mutator) {
        Objects.requireNonNull(mutator, "mutator");

        return state.updateAndGet(current -> {
                    var value = current.value();
                    mutator.accept(value);
                    return new EntryState<>(value, true, current.lastSavedAt());
                })
                .value();
    }

    public boolean dirty() {
        return currentState().dirty();
    }

    public void markDirty() {
        state.updateAndGet(current -> new EntryState<>(current.value(), true, current.lastSavedAt()));
    }

    public void markSaved() {
        state.updateAndGet(current -> new EntryState<>(current.value(), false, Instant.now()));
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    public Optional<Instant> lastSavedAt() {
        return Optional.ofNullable(currentState().lastSavedAt());
    }

    private EntryState<V> currentState() {
        return Objects.requireNonNull(state.get(), "state");
    }

    private record EntryState<V>(
            V value, boolean dirty, @Nullable Instant lastSavedAt) {
        EntryState {
            Objects.requireNonNull(value, "value");
        }
    }
}
