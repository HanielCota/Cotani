package com.cotani.teleport.policy;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.TeleportCause;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, per-cause teleport cooldown tracker.
 *
 * <p>Uses a nested {@link ConcurrentHashMap} to avoid allocating a composite key object on every
 * cooldown check.
 */
public final class TeleportCooldownService implements AutoCloseable {
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final Clock clock;
    private final Map<UUID, Map<TeleportCause, Instant>> cooldowns = new ConcurrentHashMap<>();
    private final com.cotani.task.api.SchedulerTask cleanupTask;

    public TeleportCooldownService(Clock clock, PaperTaskScheduler scheduler) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cleanupTask = scheduler.asyncTimer(this::cleanupExpired, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }

    public boolean isOnCooldown(UUID playerId, TeleportCause cause) {
        Map<TeleportCause, Instant> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) {
            return false;
        }
        Instant expiresAt = playerCooldowns.get(cause);
        if (expiresAt == null) {
            return false;
        }
        if (Instant.now(clock).isAfter(expiresAt)) {
            playerCooldowns.remove(cause, expiresAt);
            return false;
        }
        return true;
    }

    public boolean tryAcquire(UUID playerId, TeleportCause cause, Duration duration) {
        Map<TeleportCause, Instant> playerCooldowns =
                cooldowns.computeIfAbsent(playerId, _ -> new ConcurrentHashMap<>());
        Instant now = Instant.now(clock);
        Instant expiresAt = playerCooldowns.get(cause);
        if (expiresAt != null && now.isBefore(expiresAt)) {
            return false;
        }
        playerCooldowns.put(cause, now.plus(duration));
        return true;
    }

    public void put(UUID playerId, TeleportCause cause, Duration duration) {
        cooldowns
                .computeIfAbsent(playerId, _ -> new ConcurrentHashMap<>())
                .put(cause, Instant.now(clock).plus(duration));
    }

    public void clear(UUID playerId, TeleportCause cause) {
        Map<TeleportCause, Instant> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns != null) {
            playerCooldowns.remove(cause);
        }
    }

    public void clearAll(UUID playerId) {
        cooldowns.remove(playerId);
    }

    @Override
    public void close() {
        cleanupTask.cancel();
        cooldowns.clear();
    }

    private void cleanupExpired() {
        Instant now = Instant.now(clock);
        cooldowns.values().forEach(playerCooldowns -> playerCooldowns.values().removeIf(now::isAfter));
    }
}
