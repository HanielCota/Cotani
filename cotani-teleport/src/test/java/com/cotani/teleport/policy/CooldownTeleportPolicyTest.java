package com.cotani.teleport.policy;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.*;

import com.cotani.teleport.api.TeleportCause;
import com.cotani.teleport.api.TeleportContext;
import com.cotani.teleport.api.TeleportMessages;
import com.cotani.teleport.api.TeleportOptions;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class CooldownTeleportPolicyTest {

    private final TeleportCooldownService cooldownService = mock(TeleportCooldownService.class);
    private final TeleportMessages messages = TeleportMessages.defaults();
    private final CooldownTeleportPolicy policy = new CooldownTeleportPolicy(cooldownService, messages);

    @Test
    void validateOnlyChecksCooldownDoesNotAcquire() {
        TeleportContext context = contextWithCooldown(Duration.ofSeconds(5));
        when(cooldownService.isOnCooldown(context.playerId(), context.cause())).thenReturn(false);

        var result = policy.validate(context);

        assertInstanceOf(PolicyResult.Allowed.class, result);
        verify(cooldownService, never()).tryAcquire(any(), any(), any());
        verify(cooldownService, never()).put(any(), any(), any());
    }

    @Test
    void validateDeniesWhenOnCooldown() {
        TeleportContext context = contextWithCooldown(Duration.ofSeconds(5));
        when(cooldownService.isOnCooldown(context.playerId(), context.cause())).thenReturn(true);

        var result = policy.validate(context);

        assertInstanceOf(PolicyResult.Denied.class, result);
    }

    private TeleportContext contextWithCooldown(Duration duration) {
        World world = mock(World.class);
        Location target = new Location(world, 0, 64, 0);
        return new TeleportContext(
                UUID.randomUUID(),
                target,
                target,
                TeleportCause.PLUGIN_INTERNAL,
                TeleportOptions.builder()
                        .checkCooldown(true)
                        .cooldownDuration(duration)
                        .build(),
                "test",
                Instant.now());
    }
}
