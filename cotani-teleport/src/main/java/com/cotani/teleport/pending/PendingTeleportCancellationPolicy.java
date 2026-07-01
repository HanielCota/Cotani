package com.cotani.teleport.pending;

import java.util.Objects;
import org.bukkit.Location;

public final class PendingTeleportCancellationPolicy {
    private PendingTeleportCancellationPolicy() {}

    public static boolean shouldCancel(Location from, Location to) {
        if (from == null || to == null) {
            return false;
        }
        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()
                || !Objects.equals(from.getWorld(), to.getWorld());
    }
}
