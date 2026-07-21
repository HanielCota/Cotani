package com.cotani.teleport.policy;

import com.cotani.cooldown.CotaniCooldowns;
import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownService;
import com.cotani.cooldown.api.CooldownTargets;
import com.cotani.teleport.api.TeleportCause;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TeleportCooldownService implements AutoCloseable {

    private final CooldownService cooldownService;

    public TeleportCooldownService(CooldownService cooldownService) {
        this.cooldownService = Objects.requireNonNull(cooldownService, "cooldownService");
    }

    public TeleportCooldownService(Clock clock) {
        this.cooldownService = CotaniCooldowns.inMemory(Objects.requireNonNull(clock, "clock"));
    }

    public boolean isOnCooldown(UUID playerId, TeleportCause cause) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(cause, "cause");

        return cooldownService
                .user(playerId)
                .action("teleport." + cause.name().toLowerCase(Locale.ROOT))
                .check()
                .denied();
    }

    public boolean tryAcquire(UUID playerId, TeleportCause cause, Duration duration) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(duration, "duration");

        return cooldownService
                .user(playerId)
                .action("teleport." + cause.name().toLowerCase(Locale.ROOT))
                .duration(duration)
                .checkAndStart()
                .allowed();
    }

    public void put(UUID playerId, TeleportCause cause, Duration duration) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(duration, "duration");

        cooldownService
                .user(playerId)
                .action("teleport." + cause.name().toLowerCase(Locale.ROOT))
                .duration(duration)
                .start();
    }

    public void clear(UUID playerId, TeleportCause cause) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(cause, "cause");

        cooldownService.remove(
                CooldownTargets.user(playerId),
                CooldownAction.of("teleport." + cause.name().toLowerCase(Locale.ROOT)));
    }

    public void clearAll(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        for (TeleportCause cause : TeleportCause.values()) {
            clear(playerId, cause);
        }
    }

    @Override
    public void close() {
        cooldownService.clearAll();
    }
}
