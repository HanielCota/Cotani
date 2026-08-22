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
import org.bukkit.Location;

/**
 * High-performance spatial chunk grid indexer for virtual NPCs.
 * Reduces tracking overhead from O(Players * TotalNpcs) to O(Players * LocalChunkNpcs).
 */
@InternalApi
public final class NpcSpatialIndex {

    private static final String NPC_NULL_MSG = "Parameter 'npc' must not be null";
    private static final String WORLD_ID_NULL_MSG = "Parameter 'worldId' must not be null";

    public record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
        public ChunkKey {
            Objects.requireNonNull(worldId, WORLD_ID_NULL_MSG);
        }

        public static ChunkKey from(Location location) {
            var world = location.getWorld();
            var worldId = world != null ? world.getUID() : new UUID(0L, 0L);
            return new ChunkKey(worldId, location.getBlockX() >> 4, location.getBlockZ() >> 4);
        }
    }

    private final Map<ChunkKey, Set<Npc>> grid = new ConcurrentHashMap<>();
    private final Map<UUID, ChunkKey> npcLocations = new ConcurrentHashMap<>();

    public void add(Npc npc) {
        Objects.requireNonNull(npc, NPC_NULL_MSG);

        var key = ChunkKey.from(npc.location());
        npcLocations.put(npc.id(), key);
        grid.computeIfAbsent(key, _ -> ConcurrentHashMap.newKeySet()).add(npc);
    }

    public void remove(UUID npcId) {
        Objects.requireNonNull(npcId, "Parameter 'npcId' must not be null");

        var oldKey = npcLocations.remove(npcId);
        if (oldKey != null) {
            var set = grid.get(oldKey);
            if (set != null) {
                set.removeIf(n -> n.id().equals(npcId));
                if (set.isEmpty()) {
                    grid.remove(oldKey);
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

        var result = new ArrayList<Npc>();
        for (var dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (var dz = -chunkRadius; dz <= chunkRadius; dz++) {
                var key = new ChunkKey(worldId, centerChunkX + dx, centerChunkZ + dz);
                var chunkNpcs = grid.get(key);
                if (chunkNpcs != null && !chunkNpcs.isEmpty()) {
                    result.addAll(chunkNpcs);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public void clear() {
        grid.clear();
        npcLocations.clear();
    }
}
