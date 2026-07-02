package com.cotani.teleport.adapter;

import org.bukkit.entity.Player;

public interface CombatAdapter {
    static CombatAdapter noop() {
        return player -> false;
    }

    boolean isInCombat(Player player);
}
