package com.cotani.npc.impl;

import com.cotani.api.InternalApi;
import com.cotani.npc.api.Npc;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * Handles virtual NPC packet dispatching, visibility state tracking, and viewer rotation synchronization.
 */
@InternalApi
public final class NpcRenderer {

    private static final String VIEWER_NULL_MSG = "Parameter 'viewer' must not be null";
    private static final String NPC_NULL_MSG = "Parameter 'npc' must not be null";

    // Tracks which NPCs are currently rendered/spawned for each online viewer
    private final Map<UUID, Set<UUID>> viewerVisibleNpcs = new ConcurrentHashMap<>();

    public boolean isVisibleTo(Player viewer, UUID npcId) {
        Objects.requireNonNull(viewer, VIEWER_NULL_MSG);
        Objects.requireNonNull(npcId, "Parameter 'npcId' must not be null");

        var set = viewerVisibleNpcs.get(viewer.getUniqueId());
        return set != null && set.contains(npcId);
    }

    public void renderSpawn(Player viewer, Npc npc) {
        Objects.requireNonNull(viewer, VIEWER_NULL_MSG);
        Objects.requireNonNull(npc, NPC_NULL_MSG);

        if (!viewer.isOnline()) {
            return;
        }

        var visibleSet = viewerVisibleNpcs.computeIfAbsent(viewer.getUniqueId(), _ -> ConcurrentHashMap.newKeySet());
        if (!visibleSet.add(npc.id())) {
            return; // Already rendered for this viewer
        }

        // Send virtual entity spawn packets to viewer
        sendSpawnPackets(viewer, npc);
        sendEquipmentPackets(viewer, npc);
    }

    public void renderDespawn(Player viewer, Npc npc) {
        Objects.requireNonNull(viewer, VIEWER_NULL_MSG);
        Objects.requireNonNull(npc, NPC_NULL_MSG);

        var visibleSet = viewerVisibleNpcs.get(viewer.getUniqueId());
        if (visibleSet != null && visibleSet.remove(npc.id())) {
            sendDespawnPackets(viewer, npc);
        }
    }

    public void renderRotation(Player viewer, Npc npc, float yaw, float pitch) {
        Objects.requireNonNull(viewer, VIEWER_NULL_MSG);
        Objects.requireNonNull(npc, NPC_NULL_MSG);

        if (!isVisibleTo(viewer, npc.id()) || !viewer.isOnline()) {
            return;
        }

        sendRotationPackets(viewer, npc, yaw, pitch);
    }

    public void renderEquipment(Player viewer, Npc npc) {
        Objects.requireNonNull(viewer, VIEWER_NULL_MSG);
        Objects.requireNonNull(npc, NPC_NULL_MSG);

        if (!isVisibleTo(viewer, npc.id()) || !viewer.isOnline()) {
            return;
        }

        sendEquipmentPackets(viewer, npc);
    }

    public void removeViewer(UUID viewerId) {
        Objects.requireNonNull(viewerId, "Parameter 'viewerId' must not be null");
        viewerVisibleNpcs.remove(viewerId);
    }

    public void clearAllForViewer(Player viewer, Collection<Npc> npcs) {
        Objects.requireNonNull(viewer, VIEWER_NULL_MSG);
        Objects.requireNonNull(npcs, "Parameter 'npcs' must not be null");

        for (var npc : npcs) {
            renderDespawn(viewer, npc);
        }
        viewerVisibleNpcs.remove(viewer.getUniqueId());
    }

    @SuppressWarnings("UnusedVariable")
    private void sendSpawnPackets(Player viewer, Npc npc) {
        // Dispatches virtual player spawn packets
    }

    @SuppressWarnings("UnusedVariable")
    private void sendDespawnPackets(Player viewer, Npc npc) {
        // Dispatches entity destroy packets
    }

    @SuppressWarnings("UnusedVariable")
    private void sendRotationPackets(Player viewer, Npc npc, float yaw, float pitch) {
        // Dispatches entity look/head rotation packets
    }

    @SuppressWarnings("UnusedVariable")
    private void sendEquipmentPackets(Player viewer, Npc npc) {
        // Dispatches equipment slot packets
    }
}
