package com.cotani.teleport.adapter;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface RegionProtectionAdapter {
    boolean canTeleport(Player player, Location target);

    static RegionProtectionAdapter noop() {
        return (player, target) -> true;
    }
}
