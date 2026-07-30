package com.cotani.teleport.pending;

import com.cotani.api.InternalApi;
import com.cotani.teleport.util.Locations;
import org.bukkit.Location;

@InternalApi
public final class PendingTeleportCancellationPolicy {
    private PendingTeleportCancellationPolicy() {}

    public static boolean shouldCancel(Location from, Location to) {
        return !Locations.sameBlock(from, to);
    }
}
