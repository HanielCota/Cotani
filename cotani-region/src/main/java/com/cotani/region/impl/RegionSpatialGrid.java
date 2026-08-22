package com.cotani.region.impl;

import com.cotani.api.InternalApi;
import com.cotani.region.api.Region3D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;

/**
 * High-performance 3D spatial chunk grid for rapid region containment queries.
 */
@InternalApi
public final class RegionSpatialGrid {

    public record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
        public ChunkKey {
            Objects.requireNonNull(worldId, "Parameter 'worldId' must not be null");
        }
    }

    private final Map<String, Region3D> regionsById = new ConcurrentHashMap<>();
    private final Map<ChunkKey, Set<Region3D>> chunkGrid = new ConcurrentHashMap<>();

    public void add(Region3D region) {
        Objects.requireNonNull(region, "Parameter 'region' must not be null");

        remove(region.id());
        regionsById.put(region.id(), region);

        var minCx = region.minX() >> 4;
        var maxCx = region.maxX() >> 4;
        var minCz = region.minZ() >> 4;
        var maxCz = region.maxZ() >> 4;

        for (var cx = minCx; cx <= maxCx; cx++) {
            for (var cz = minCz; cz <= maxCz; cz++) {
                var key = new ChunkKey(region.worldId(), cx, cz);
                chunkGrid
                        .computeIfAbsent(key, _ -> ConcurrentHashMap.newKeySet())
                        .add(region);
            }
        }
    }

    public boolean remove(String regionId) {
        Objects.requireNonNull(regionId, "Parameter 'regionId' must not be null");

        var removed = regionsById.remove(regionId);
        if (removed == null) {
            return false;
        }

        var minCx = removed.minX() >> 4;
        var maxCx = removed.maxX() >> 4;
        var minCz = removed.minZ() >> 4;
        var maxCz = removed.maxZ() >> 4;

        for (var cx = minCx; cx <= maxCx; cx++) {
            for (var cz = minCz; cz <= maxCz; cz++) {
                var key = new ChunkKey(removed.worldId(), cx, cz);
                var set = chunkGrid.get(key);
                if (set != null) {
                    set.removeIf(r -> r.id().equals(regionId));
                    if (set.isEmpty()) {
                        chunkGrid.remove(key);
                    }
                }
            }
        }
        return true;
    }

    public Optional<Region3D> find(String regionId) {
        Objects.requireNonNull(regionId, "Parameter 'regionId' must not be null");
        return Optional.ofNullable(regionsById.get(regionId));
    }

    public Collection<Region3D> all() {
        return Collections.unmodifiableCollection(regionsById.values());
    }

    public List<Region3D> regionsAt(Location location) {
        Objects.requireNonNull(location, "Parameter 'location' must not be null");

        var world = location.getWorld();
        if (world == null) {
            return List.of();
        }

        var key = new ChunkKey(world.getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        var candidates = chunkGrid.get(key);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        var matches = new ArrayList<Region3D>();
        for (var region : candidates) {
            if (region.contains(location)) {
                matches.add(region);
            }
        }

        matches.sort(Comparator.comparingInt(Region3D::priority).reversed());
        return Collections.unmodifiableList(matches);
    }

    public void clear() {
        regionsById.clear();
        chunkGrid.clear();
    }
}
