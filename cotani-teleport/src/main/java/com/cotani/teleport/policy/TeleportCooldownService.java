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

    private static final String PLAYER_ID_PARAM = "playerId";
    private static final String CAUSE_PARAM = "cause";
    private static final String TELEPORT_PREFIX = "teleport.";

    private final CooldownService cooldownService;

    public TeleportCooldownService(CooldownService cooldownService) {
        this.cooldownService = Objects.requireNonNull(cooldownService, "cooldownService");
    }

    public TeleportCooldownService(Clock clock) {
        this.cooldownService = CotaniCooldowns.inMemory(Objects.requireNonNull(clock, "clock"));
    }

    public boolean isOnCooldown(UUID playerId, TeleportCause cause) {
        Objects.requireNonNull(playerId, PLAYER_ID_PARAM);
        Objects.requireNonNull(cause, CAUSE_PARAM);

        return cooldownService
                .user(playerId)
                .action(TELEPORT_PREFIX + cause.name().toLowerCase(Locale.ROOT))
                .check()
                .denied();
    }

    public boolean tryAcquire(UUID playerId, TeleportCause cause, Duration duration) {
        Objects.requireNonNull(playerId, PLAYER_ID_PARAM);
        Objects.requireNonNull(cause, CAUSE_PARAM);
        Objects.requireNonNull(duration, "duration");

        return cooldownService
                .user(playerId)
                .action(TELEPORT_PREFIX + cause.name().toLowerCase(Locale.ROOT))
                .duration(duration)
                .checkAndStart()
                .allowed();
    }

    public void put(UUID playerId, TeleportCause cause, Duration duration) {
        Objects.requireNonNull(playerId, PLAYER_ID_PARAM);
        Objects.requireNonNull(cause, CAUSE_PARAM);
        Objects.requireNonNull(duration, "duration");

        cooldownService
                .user(playerId)
                .action(TELEPORT_PREFIX + cause.name().toLowerCase(Locale.ROOT))
                .duration(duration)
                .start();
    }

    public void clear(UUID playerId, TeleportCause cause) {
        Objects.requireNonNull(playerId, PLAYER_ID_PARAM);
        Objects.requireNonNull(cause, CAUSE_PARAM);

        cooldownService.remove(
                CooldownTargets.user(playerId),
                CooldownAction.of(TELEPORT_PREFIX + cause.name().toLowerCase(Locale.ROOT)));
    }

    public void clearAll(UUID playerId) {
        Objects.requireNonNull(playerId, PLAYER_ID_PARAM);
        for (TeleportCause cause : TeleportCause.values()) {
            clear(playerId, cause);
        }
    }

    @Override
    public void close() {
        cooldownService.clearAll();
    }
}
