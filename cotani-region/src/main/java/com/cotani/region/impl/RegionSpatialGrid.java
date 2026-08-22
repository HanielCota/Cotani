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
 * High-performance 3D spatial chunk grid for rapid region containment queries with zero-allocation chunk coordinate packing.
 */
@InternalApi
public final class RegionSpatialGrid {

    private final Map<String, Region3D> regionsById = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, Set<Region3D>>> chunkGrid = new ConcurrentHashMap<>();

    public static long packChunk(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public void add(Region3D region) {
        Objects.requireNonNull(region, "Parameter 'region' must not be null");

        remove(region.id());
        regionsById.put(region.id(), region);

        var minCx = region.minX() >> 4;
        var maxCx = region.maxX() >> 4;
        var minCz = region.minZ() >> 4;
        var maxCz = region.maxZ() >> 4;

        var worldGrid = chunkGrid.computeIfAbsent(region.worldId(), _ -> new ConcurrentHashMap<>());

        for (var cx = minCx; cx <= maxCx; cx++) {
            for (var cz = minCz; cz <= maxCz; cz++) {
                var packed = packChunk(cx, cz);
                worldGrid
                        .computeIfAbsent(packed, _ -> ConcurrentHashMap.newKeySet())
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

        var worldGrid = chunkGrid.get(removed.worldId());
        if (worldGrid != null) {
            for (var cx = minCx; cx <= maxCx; cx++) {
                for (var cz = minCz; cz <= maxCz; cz++) {
                    var packed = packChunk(cx, cz);
                    var set = worldGrid.get(packed);
                    if (set != null) {
                        set.removeIf(r -> r.id().equals(regionId));
                        if (set.isEmpty()) {
                            worldGrid.remove(packed);
                        }
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

        var worldGrid = chunkGrid.get(world.getUID());
        if (worldGrid == null || worldGrid.isEmpty()) {
            return List.of();
        }

        var packed = packChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        var candidates = worldGrid.get(packed);
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
