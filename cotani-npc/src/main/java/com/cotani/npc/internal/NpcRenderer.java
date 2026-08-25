package com.cotani.npc.internal;

import com.cotani.api.InternalApi;
import com.cotani.npc.api.Npc;
import com.cotani.npc.api.NpcPacketAdapter;
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

    private final NpcPacketAdapter packetAdapter;
    // Tracks which NPCs are currently rendered/spawned for each online viewer
    private final Map<UUID, Set<UUID>> viewerVisibleNpcs = new ConcurrentHashMap<>();

    public NpcRenderer() {
        this(NpcPacketAdapter.noop());
    }

    public NpcRenderer(NpcPacketAdapter packetAdapter) {
        this.packetAdapter = Objects.requireNonNull(packetAdapter, "packetAdapter");
    }

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

    private void sendSpawnPackets(Player viewer, Npc npc) {
        packetAdapter.sendSpawn(viewer, npc);
    }

    private void sendDespawnPackets(Player viewer, Npc npc) {
        packetAdapter.sendDespawn(viewer, npc);
    }

    private void sendRotationPackets(Player viewer, Npc npc, float yaw, float pitch) {
        packetAdapter.sendRotation(viewer, npc, yaw, pitch);
    }

    private void sendEquipmentPackets(Player viewer, Npc npc) {
        packetAdapter.sendEquipment(viewer, npc);
    }
}
