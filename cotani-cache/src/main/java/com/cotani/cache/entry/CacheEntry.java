package com.cotani.cache.entry;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
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
        this.state = new AtomicReference<>(new EntryState<>(Objects.requireNonNull(value, "value"), false, null, 0));
        this.loadedAt = Instant.now();
    }

    public V value() {
        return currentState().value();
    }

    /**
     * Updates the value and returns {@code true} if the entry transitioned from clean to dirty.
     */
    public boolean update(UnaryOperator<V> updater) {
        Objects.requireNonNull(updater, "updater");

        var becameDirty = new AtomicBoolean(false);
        state.updateAndGet(current -> {
            var updated = Objects.requireNonNull(updater.apply(current.value()), "updated");
            becameDirty.set(!current.dirty());
            return new EntryState<>(updated, true, current.lastSavedAt(), current.version() + 1);
        });
        return becameDirty.get();
    }

    /**
     * Mutates the value in place and returns {@code true} if the entry transitioned from clean to dirty.
     */
    public boolean mutate(Consumer<V> mutator) {
        Objects.requireNonNull(mutator, "mutator");

        var becameDirty = new AtomicBoolean(false);
        state.updateAndGet(current -> {
            var value = current.value();
            mutator.accept(value);
            becameDirty.set(!current.dirty());
            return new EntryState<>(value, true, current.lastSavedAt(), current.version() + 1);
        });
        return becameDirty.get();
    }

    public boolean dirty() {
        return currentState().dirty();
    }

    /**
     * Marks the entry as dirty and returns {@code true} if it transitioned from clean to dirty.
     */
    public boolean markDirty() {
        var becameDirty = new AtomicBoolean(false);
        state.updateAndGet(current -> {
            becameDirty.set(!current.dirty());
            return new EntryState<>(current.value(), true, current.lastSavedAt(), current.version() + 1);
        });
        return becameDirty.get();
    }

    public void markSaved() {
        state.updateAndGet(current -> new EntryState<>(current.value(), false, Instant.now(), current.version()));
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
    public boolean markSavedIfVersionMatches(long expectedVersion) {
        while (true) {
            EntryState<V> current = Objects.requireNonNull(state.get(), "state");
            if (current.version() != expectedVersion || !current.dirty()) {
                return false;
            }
            EntryState<V> updated = new EntryState<>(current.value(), false, Instant.now(), expectedVersion);
            if (state.compareAndSet(current, updated)) {
                return true;
            }
        }
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
