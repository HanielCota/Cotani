package com.cotani.teleport.policy;

import com.cotani.teleport.api.TeleportContext;
import com.cotani.teleport.api.TeleportFailureReason;
import com.cotani.teleport.api.TeleportMessages;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PermissionTeleportPolicy implements TeleportPolicy {
    private final String permission;
    private final TeleportMessages messages;

    public PermissionTeleportPolicy(String permission, TeleportMessages messages) {
        this.permission = permission;
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public PolicyResult validate(TeleportContext context) {
        if (!context.options().checkPermission()) {
            return PolicyResult.allowed();
        }
        Player player = Bukkit.getPlayer(context.playerId());
        if (player == null || !player.hasPermission(permission)) {
            return PolicyResult.denied(TeleportFailureReason.BLOCKED_BY_PERMISSION, messages.blockedByPermission());
        }
        return PolicyResult.allowed();
    }
}
