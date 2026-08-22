package com.cotani.npc.impl;

import com.cotani.api.InternalApi;
import com.cotani.npc.api.Npc;
import java.util.Collection;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Handles distance tracking, visibility culling, and mathematical look-at rotation for NPCs.
 */
@InternalApi
public final class NpcTracker {

    private final NpcRenderer renderer;

    public NpcTracker(NpcRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "Parameter 'renderer' must not be null");
    }

    public void trackViewer(Player viewer, Collection<Npc> npcs) {
        Objects.requireNonNull(viewer, "Parameter 'viewer' must not be null");
        Objects.requireNonNull(npcs, "Parameter 'npcs' must not be null");

        if (!viewer.isOnline()) {
            return;
        }

        var playerLoc = viewer.getLocation();
        if (playerLoc == null) {
            return;
        }
        var playerWorld = playerLoc.getWorld();
        if (playerWorld == null) {
            return;
        }

        for (var npc : npcs) {
            var npcLoc = npc.location();
            var npcWorld = npcLoc.getWorld();

            if (!Objects.equals(playerWorld, npcWorld)) {
                if (renderer.isVisibleTo(viewer, npc.id())) {
                    renderer.renderDespawn(viewer, npc);
                }
                continue;
            }

            var distanceSquared = playerLoc.distanceSquared(npcLoc);
            var maxDistance = npc.viewDistance();
            var maxDistanceSquared = maxDistance * maxDistance;

            if (distanceSquared > maxDistanceSquared) {
                // Out of range
                if (renderer.isVisibleTo(viewer, npc.id())) {
                    renderer.renderDespawn(viewer, npc);
                }
                continue;
            }

            // In range
            if (!renderer.isVisibleTo(viewer, npc.id())) {
                renderer.renderSpawn(viewer, npc);
            }

            if (npc.lookAtPlayer()) {
                var rotation = calculateLookAt(npcLoc, viewer.getEyeLocation());
                renderer.renderRotation(viewer, npc, rotation[0], rotation[1]);
            }
        }
    }

    /**
     * Calculates yaw and pitch rotation from source location facing target location.
     *
     * @param source NPC head origin location
     * @param target Player eye target location
     * @return float array with [yaw, pitch]
     */
    public static float[] calculateLookAt(Location source, Location target) {
        var dx = target.getX() - source.getX();
        var dy = target.getY() - (source.getY() + 1.62);
        var dz = target.getZ() - source.getZ();

        var distanceXZ = Math.sqrt(dx * dx + dz * dz);
        if (distanceXZ < 0.0001) {
            return new float[] {source.getYaw(), source.getPitch()};
        }

        var yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        var pitch = Math.clamp((float) Math.toDegrees(-Math.atan2(dy, distanceXZ)), -90.0f, 90.0f);

        return new float[] {yaw, pitch};
    }
}
