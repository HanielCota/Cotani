package com.cotani.teleport.adapter;

import org.bukkit.entity.Player;

public interface CombatAdapter {
    boolean isInCombat(Player player);

    static CombatAdapter noop() {
        return player -> false;
    }
}
