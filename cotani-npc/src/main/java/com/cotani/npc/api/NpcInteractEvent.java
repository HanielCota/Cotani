package com.cotani.npc.api;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Event data passed when a player interacts with a virtual NPC.
 *
 * @param player the player who interacted with the NPC
 * @param npc the NPC that was interacted with
 * @param hand the hand used for the interaction (e.g. HAND or OFF_HAND)
 * @param action the type of interaction (ATTACK or INTERACT)
 */
public record NpcInteractEvent(Player player, Npc npc, EquipmentSlot hand, Action action) {

    public NpcInteractEvent {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");
        Objects.requireNonNull(npc, "Parameter 'npc' must not be null");
        Objects.requireNonNull(hand, "Parameter 'hand' must not be null");
        Objects.requireNonNull(action, "Parameter 'action' must not be null");
    }

    /**
     * Interaction action type.
     */
    public enum Action {
        /**
         * Left-click / attack.
         */
        LEFT_CLICK,

        /**
         * Right-click / interact.
         */
        RIGHT_CLICK
    }
}
