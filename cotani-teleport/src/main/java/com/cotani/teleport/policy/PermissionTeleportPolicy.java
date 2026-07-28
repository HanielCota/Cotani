package com.cotani.teleport.policy;

import com.cotani.teleport.api.PlayerResolver;
import com.cotani.teleport.api.TeleportContext;
import com.cotani.teleport.api.TeleportFailureReason;
import com.cotani.teleport.api.TeleportMessages;
import java.util.Objects;
import org.bukkit.entity.Player;

public final class PermissionTeleportPolicy implements TeleportPolicy {
    private final String permission;
    private final TeleportMessages messages;
    private final PlayerResolver playerResolver;

    public PermissionTeleportPolicy(String permission, TeleportMessages messages) {
        this(permission, messages, PlayerResolver.bukkit());
    }

    public PermissionTeleportPolicy(String permission, TeleportMessages messages, PlayerResolver playerResolver) {
        this.permission = Objects.requireNonNull(permission, "permission");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.playerResolver = Objects.requireNonNull(playerResolver, "playerResolver");
    }

    @Override
    public PolicyResult validate(TeleportContext context) {
        if (!context.options().checkPermission()) {
            return PolicyResult.allowed();
        }
        Player player = playerResolver.resolve(context.playerId());
        if (player == null || !player.hasPermission(permission)) {
            return PolicyResult.denied(TeleportFailureReason.BLOCKED_BY_PERMISSION, messages.blockedByPermission());
        }
        return PolicyResult.allowed();
    }
}
