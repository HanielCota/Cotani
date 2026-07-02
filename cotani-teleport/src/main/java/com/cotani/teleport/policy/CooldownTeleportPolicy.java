package com.cotani.teleport.policy;

import com.cotani.teleport.api.TeleportContext;
import com.cotani.teleport.api.TeleportFailureReason;
import com.cotani.teleport.api.TeleportMessages;
import java.util.Objects;

public final class CooldownTeleportPolicy implements TeleportPolicy {
    private final TeleportCooldownService cooldownService;
    private final TeleportMessages messages;

    public CooldownTeleportPolicy(TeleportCooldownService cooldownService, TeleportMessages messages) {
        this.cooldownService = cooldownService;
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public PolicyResult validate(TeleportContext context) {
        if (!context.options().checkCooldown()) {
            return PolicyResult.allowed();
        }
        if (cooldownService.isOnCooldown(context.playerId(), context.cause())) {
            return PolicyResult.denied(TeleportFailureReason.BLOCKED_BY_COOLDOWN, messages.blockedByCooldown());
        }
        return PolicyResult.allowed();
    }
}
