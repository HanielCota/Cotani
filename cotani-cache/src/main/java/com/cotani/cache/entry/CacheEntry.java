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

    private CacheEntry(V value) {
        this.state = new AtomicReference<>(new EntryState<>(Objects.requireNonNull(value, "value"), false, null, 0));
        this.loadedAt = Instant.now();
    }

    public static <V> CacheEntry<V> of(V value) {
        return new CacheEntry<>(value);
    }

    public V value() {
        return currentState().value();
    }

    public synchronized boolean update(UnaryOperator<V> updater) {
        Objects.requireNonNull(updater, "updater");

        EntryState<V> current = currentState();
        V currentValue = current.value();
        V updated = Objects.requireNonNull(updater.apply(currentValue), "updated");

        if (Objects.equals(currentValue, updated)) {
            return false;
        }

        state.set(new EntryState<>(updated, true, current.lastSavedAt(), current.version() + 1));

        return !current.dirty();
    }

    public synchronized boolean mutate(Consumer<V> mutator) {
        Objects.requireNonNull(mutator, "mutator");

        EntryState<V> current = currentState();
        V value = current.value();
        mutator.accept(value);
        state.set(new EntryState<>(value, true, current.lastSavedAt(), current.version() + 1));

        return !current.dirty();
    }

    public boolean dirty() {
        return currentState().dirty();
    }

    /**
     * Marks the entry as dirty and returns {@code true} if it transitioned from clean to dirty.
     */
    public synchronized boolean markDirty() {
        EntryState<V> current = currentState();
        state.set(new EntryState<>(current.value(), true, current.lastSavedAt(), current.version() + 1));

        return !current.dirty();
    }

    public synchronized void markSaved() {
        EntryState<V> current = currentState();
        state.set(new EntryState<>(current.value(), false, Instant.now(), current.version()));
    }

    /**
     * Marks this entry as saved only if its current version matches {@code expectedVersion}.
     *
     * <p>This prevents an old save from clearing a newer modification: if the entry was mutated after
     * the save started, the version will have changed and the mark is skipped.
     *
     * @param expectedVersion the version observed when the save began
     * @return {@code true} if the entry was marked saved
     */
    public synchronized boolean markSavedIfVersionMatches(long expectedVersion) {
        EntryState<V> current = currentState();

        if (current.version() != expectedVersion || !current.dirty()) {
            return false;
        }

        state.set(new EntryState<>(current.value(), false, Instant.now(), expectedVersion));

        return true;
    }

    public long version() {
        return currentState().version();
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
            V value, boolean dirty, @Nullable Instant lastSavedAt, long version) {
        EntryState {
            Objects.requireNonNull(value, "value");
        }
    }
}
