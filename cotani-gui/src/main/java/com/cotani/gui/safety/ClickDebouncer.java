package com.cotani.gui.safety;

import com.cotani.api.InternalApi;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enforces per-player click debounce timing independently of Bukkit event listeners.
 */
@InternalApi
public final class ClickDebouncer {
    private final long debounceNanos;
    private final ConcurrentMap<UUID, AtomicLong> lastClickByPlayer = new ConcurrentHashMap<>();

    /**
     * Creates a debouncer with the given debounce duration.
     *
     * @param debounce minimum interval between accepted clicks per player
     */
    public ClickDebouncer(Duration debounce) {
        Objects.requireNonNull(debounce, "Parameter 'debounce' must not be null");

        if (debounce.isNegative()) {
            throw new IllegalArgumentException("debounce cannot be negative: " + debounce);
        }

        this.debounceNanos = debounce.toNanos();
    }

    /**
     * Attempts to acquire a click token for the player if the debounce interval has elapsed.
     *
     * @param playerId unique identifier of the player
     * @return {@code true} if the click is accepted, {@code false} if debounced
     */
    public boolean tryAcquire(UUID playerId) {
        Objects.requireNonNull(playerId, "Parameter 'playerId' must not be null");

        var lastClick = lastClickByPlayer.computeIfAbsent(playerId, _ -> new AtomicLong(Long.MIN_VALUE));

        var now = System.nanoTime();
        while (true) {
            long previous = lastClick.get();
            if (previous != Long.MIN_VALUE && now - previous < debounceNanos) {
                return false;
            }
            if (lastClick.compareAndSet(previous, now)) {
                return true;
            }
        }
    }

    /**
     * Resets debounce state for a specific player (e.g. on quit or inventory close)
     * without removing the map entry, avoiding re-allocation churn.
     *
     * @param playerId unique identifier of the player
     */
    public void release(UUID playerId) {
        Objects.requireNonNull(playerId, "Parameter 'playerId' must not be null");

        var lastClick = lastClickByPlayer.get(playerId);

        if (lastClick != null) {
            lastClick.set(Long.MIN_VALUE);
        }
    }

    /**
     * Removes debounce state for a specific player (e.g. on player quit)
     * to avoid retaining disconnected player entries in memory.
     *
     * @param playerId unique identifier of the player
     */
    public void remove(UUID playerId) {
        Objects.requireNonNull(playerId, "Parameter 'playerId' must not be null");

        lastClickByPlayer.remove(playerId);
    }

    /**
     * Clears all player debounce state.
     */
    public void clear() {
        lastClickByPlayer.clear();
    }
}
