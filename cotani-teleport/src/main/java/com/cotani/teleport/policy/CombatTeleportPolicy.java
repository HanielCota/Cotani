package com.cotani.teleport.policy;

import com.cotani.teleport.adapter.CombatAdapter;
import com.cotani.teleport.api.PlayerResolver;
import com.cotani.teleport.api.TeleportContext;
import com.cotani.teleport.api.TeleportFailureReason;
import com.cotani.teleport.api.TeleportMessages;
import java.util.Objects;
import org.bukkit.entity.Player;

public final class CombatTeleportPolicy implements TeleportPolicy {
    private final CombatAdapter combatAdapter;
    private final TeleportMessages messages;
    private final PlayerResolver playerResolver;

    public CombatTeleportPolicy(CombatAdapter combatAdapter, TeleportMessages messages) {
        this(combatAdapter, messages, PlayerResolver.bukkit());
    }

    public CombatTeleportPolicy(CombatAdapter combatAdapter, TeleportMessages messages, PlayerResolver playerResolver) {
        this.combatAdapter = Objects.requireNonNull(combatAdapter, "combatAdapter");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.playerResolver = Objects.requireNonNull(playerResolver, "playerResolver");
    }

    @Override
    public PolicyResult validate(TeleportContext context) {
        if (!context.options().checkCombat()) {
            return PolicyResult.allowed();
        }
        Player player = playerResolver.resolve(context.playerId());
        if (player != null && combatAdapter.isInCombat(player)) {
            return PolicyResult.denied(TeleportFailureReason.BLOCKED_BY_COMBAT, messages.blockedByCombat());
        }
        return PolicyResult.allowed();
    }
}
