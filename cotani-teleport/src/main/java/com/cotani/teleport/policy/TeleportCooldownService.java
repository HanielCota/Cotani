package com.cotani.teleport.policy;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.TeleportCause;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportCooldownService implements AutoCloseable {
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(5);

    private final Clock clock;
    private final Map<CooldownKey, Instant> cooldowns = new ConcurrentHashMap<>();
    private final com.cotani.task.api.SchedulerTask cleanupTask;

    public TeleportCooldownService(Clock clock, PaperTaskScheduler scheduler) {
        this.clock = clock;
        this.cleanupTask = scheduler.asyncTimer(this::cleanupExpired, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }

    public boolean isOnCooldown(UUID playerId, TeleportCause cause) {
        Instant expiresAt = cooldowns.get(new CooldownKey(playerId, cause));
        if (expiresAt == null) {
            return false;
        }
        if (Instant.now(clock).isAfter(expiresAt)) {
            cooldowns.remove(new CooldownKey(playerId, cause), expiresAt);
            return false;
        }
        return true;
    }

    public boolean tryAcquire(UUID playerId, TeleportCause cause, Duration duration) {
        var key = new CooldownKey(playerId, cause);
        Instant now = Instant.now(clock);
        Instant expiresAt = cooldowns.get(key);
        if (expiresAt != null && now.isBefore(expiresAt)) {
            return false;
        }
        cooldowns.put(key, now.plus(duration));
        return true;
    }

    public void put(UUID playerId, TeleportCause cause, Duration duration) {
        cooldowns.put(new CooldownKey(playerId, cause), Instant.now(clock).plus(duration));
    }

    public void clear(UUID playerId, TeleportCause cause) {
        cooldowns.remove(new CooldownKey(playerId, cause));
    }

    public void clearAll(UUID playerId) {
        cooldowns.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    @Override
    public void close() {
        cleanupTask.cancel();
        cooldowns.clear();
    }

    private void cleanupExpired() {
        Instant now = Instant.now(clock);
        cooldowns.values().removeIf(now::isAfter);
    }

    private record CooldownKey(UUID playerId, TeleportCause cause) {}
}
