package com.cotani.teleport.policy;

import com.cotani.teleport.adapter.RegionProtectionAdapter;
import com.cotani.teleport.api.TeleportContext;
import com.cotani.teleport.api.TeleportFailureReason;
import com.cotani.teleport.api.TeleportMessages;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class RegionTeleportPolicy implements TeleportPolicy {
    private final RegionProtectionAdapter regionAdapter;
    private final TeleportMessages messages;

    public RegionTeleportPolicy(RegionProtectionAdapter regionAdapter, TeleportMessages messages) {
        this.regionAdapter = regionAdapter;
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public PolicyResult validate(TeleportContext context) {
        if (!context.options().checkRegion()) {
            return PolicyResult.allowed();
        }
        Player player = Bukkit.getPlayer(context.playerId());
        if (player == null || !regionAdapter.canTeleport(player, context.target())) {
            return PolicyResult.denied(TeleportFailureReason.BLOCKED_BY_REGION, messages.blockedByRegion());
        }
        return PolicyResult.allowed();
    }
}
