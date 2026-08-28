package com.cotani.region.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.region.api.Region3D;
import com.cotani.region.api.RegionFlag;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class RegionSpatialGridStressTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Test
    void oneThousandRegionsCanBeIndexedQueriedAndRemovedConcurrently() {
        var grid = new RegionSpatialGrid();
        var world = mock(World.class);
        var worldId = UUID.fromString("cccccccc-0000-0000-0000-000000000001");
        when(world.getUID()).thenReturn(worldId);
        int regions = StressTestSupport.MINIMUM_ITERATIONS;

        StressTestSupport.concurrent("region", "concurrent-index", regions, 32, TIMEOUT, index -> {
            int min = index * 32;
            grid.add(Region3D.builder("region-" + index, worldId)
                    .bounds(min, -64, min, min + 15, 320, min + 15)
                    .priority(index % 17)
                    .flag(RegionFlag.ENTRY, index % 2 == 0)
                    .build());
            return CompletableFuture.completedFuture(Boolean.TRUE);
        });
        assertEquals(regions, grid.all().size());

        var queries = StressTestSupport.concurrent(
                "region",
                "concurrent-query",
                regions,
                32,
                TIMEOUT,
                index -> CompletableFuture.completedFuture(
                        grid.regionsAt(new Location(world, index * 32 + 8, 64, index * 32 + 8))));
        for (int index = 0; index < regions; index++) {
            assertEquals(1, queries.get(index).size());
            assertEquals("region-" + index, queries.get(index).getFirst().id());
        }

        StressTestSupport.concurrent(
                "region",
                "concurrent-remove",
                regions,
                32,
                TIMEOUT,
                index -> CompletableFuture.completedFuture(grid.remove("region-" + index)));
        assertTrue(grid.all().isEmpty());
    }

    @Test
    void generatedBoundaryPositionsRespectInclusiveRegionBounds() {
        StressTestSupport.scenarios("region", "containment-boundary", (context, random, player) -> {
            var world = mock(World.class);
            var worldId = random.uuid("world");
            when(world.getUID()).thenReturn(worldId);
            int minX = random.nextInt(-1_000_000, 1_000_001);
            int minZ = random.nextInt(-1_000_000, 1_000_001);
            int size = random.nextInt(1, 513);
            var region = Region3D.builder("generated-" + context.iteration(), worldId)
                    .bounds(minX, -64, minZ, minX + size, 320, minZ + size)
                    .build();

            assertTrue(region.contains(new Location(world, minX, -64, minZ)), context::description);
            assertTrue(region.contains(new Location(world, minX + size, 320, minZ + size)), context::description);
            assertEquals((long) (size + 1) * 385L * (size + 1), region.volume(), context::description);
        });
    }
}
