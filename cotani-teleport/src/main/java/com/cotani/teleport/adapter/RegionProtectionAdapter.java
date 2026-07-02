package com.cotani.teleport.adapter;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface RegionProtectionAdapter {
    static RegionProtectionAdapter noop() {
        return (player, target) -> true;
    }

    boolean canTeleport(Player player, Location target);
}
