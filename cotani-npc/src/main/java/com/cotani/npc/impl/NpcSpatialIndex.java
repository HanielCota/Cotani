package com.cotani.npc.impl;

import com.cotani.api.InternalApi;
import com.cotani.npc.api.Npc;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance spatial chunk grid indexer for virtual NPCs with zero-allocation packed chunk coordinate hashing.
 * Reduces tracking overhead from O(Players * TotalNpcs) to O(Players * LocalChunkNpcs).
 */
@InternalApi
public final class NpcSpatialIndex {

    private static final String NPC_NULL_MSG = "Parameter 'npc' must not be null";
    private static final String WORLD_ID_NULL_MSG = "Parameter 'worldId' must not be null";

    private final Map<UUID, Map<Long, Set<Npc>>> grid = new ConcurrentHashMap<>();
    private final Map<UUID, Long> npcChunkPositions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> npcWorldIds = new ConcurrentHashMap<>();

    public static long packChunk(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public void add(Npc npc) {
        Objects.requireNonNull(npc, NPC_NULL_MSG);

        var loc = npc.location();
        var world = loc.getWorld();
        var worldId = world != null ? world.getUID() : new UUID(0L, 0L);
        var packed = packChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);

        npcWorldIds.put(npc.id(), worldId);
        npcChunkPositions.put(npc.id(), packed);

        grid.computeIfAbsent(worldId, _ -> new ConcurrentHashMap<>())
                .computeIfAbsent(packed, _ -> ConcurrentHashMap.newKeySet())
                .add(npc);
    }

    public void remove(UUID npcId) {
        Objects.requireNonNull(npcId, "Parameter 'npcId' must not be null");

        var worldId = npcWorldIds.remove(npcId);
        var packed = npcChunkPositions.remove(npcId);

        if (worldId != null && packed != null) {
            var worldGrid = grid.get(worldId);
            if (worldGrid != null) {
                var set = worldGrid.get(packed);
                if (set != null) {
                    set.removeIf(n -> n.id().equals(npcId));
                    if (set.isEmpty()) {
                        worldGrid.remove(packed);
                    }
                }
            }
        }
    }

    public void update(Npc npc) {
        Objects.requireNonNull(npc, NPC_NULL_MSG);

        remove(npc.id());
        add(npc);
    }

    public List<Npc> getNearby(UUID worldId, int centerChunkX, int centerChunkZ, int chunkRadius) {
        Objects.requireNonNull(worldId, WORLD_ID_NULL_MSG);

        var worldGrid = grid.get(worldId);
        if (worldGrid == null || worldGrid.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<Npc>();
        for (var dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (var dz = -chunkRadius; dz <= chunkRadius; dz++) {
                var packed = packChunk(centerChunkX + dx, centerChunkZ + dz);
                var chunkNpcs = worldGrid.get(packed);
                if (chunkNpcs != null && !chunkNpcs.isEmpty()) {
                    result.addAll(chunkNpcs);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public void clear() {
        grid.clear();
        npcChunkPositions.clear();
        npcWorldIds.clear();
    }
}
