package com.cotani.teleport.policy;

import com.cotani.teleport.adapter.CombatAdapter;
import com.cotani.teleport.api.TeleportContext;
import com.cotani.teleport.api.TeleportFailureReason;
import com.cotani.teleport.api.TeleportMessages;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class CombatTeleportPolicy implements TeleportPolicy {
    private final CombatAdapter combatAdapter;
    private final TeleportMessages messages;

    public CombatTeleportPolicy(CombatAdapter combatAdapter, TeleportMessages messages) {
        this.combatAdapter = combatAdapter;
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public PolicyResult validate(TeleportContext context) {
        if (!context.options().checkCombat()) {
            return PolicyResult.allowed();
        }
        Player player = Bukkit.getPlayer(context.playerId());
        if (player != null && combatAdapter.isInCombat(player)) {
            return PolicyResult.denied(TeleportFailureReason.BLOCKED_BY_COMBAT, messages.blockedByCombat());
        }
        return PolicyResult.allowed();
    }
}
