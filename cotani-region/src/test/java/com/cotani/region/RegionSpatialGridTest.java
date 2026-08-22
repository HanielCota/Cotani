package com.cotani.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.region.api.Region3D;
import com.cotani.region.impl.RegionSpatialGrid;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegionSpatialGridTest {

    private RegionSpatialGrid grid;
    private World world;
    private UUID worldId;

    @BeforeEach
    void setUp() {
        grid = new RegionSpatialGrid();
        world = mock(World.class);
        worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
    }

    @Test
    void shouldIndexAndSortByPriority() {
        var outerRegion = Region3D.builder("outer", worldId)
                .bounds(0, 0, 0, 100, 100, 100)
                .priority(1)
                .build();

        var innerVipRegion = Region3D.builder("inner-vip", worldId)
                .bounds(20, 20, 20, 40, 40, 40)
                .priority(10)
                .build();

        grid.add(outerRegion);
        grid.add(innerVipRegion);

        var queryLocInsideBoth = new Location(world, 30, 30, 30);
        var regions = grid.regionsAt(queryLocInsideBoth);

        assertEquals(2, regions.size());
        assertEquals("inner-vip", regions.getFirst().id()); // Priority 10 first
        assertEquals("outer", regions.getLast().id()); // Priority 1 second

        var queryLocOuterOnly = new Location(world, 5, 5, 5);
        var outerOnly = grid.regionsAt(queryLocOuterOnly);
        assertEquals(1, outerOnly.size());
        assertEquals("outer", outerOnly.getFirst().id());
    }

    @Test
    void shouldRemoveAndClearGrid() {
        var region =
                Region3D.builder("test", worldId).bounds(0, 0, 0, 15, 15, 15).build();

        grid.add(region);
        assertTrue(grid.find("test").isPresent());

        var removed = grid.remove("test");
        assertTrue(removed);
        assertTrue(grid.find("test").isEmpty());
        assertFalse(grid.remove("test"));
    }
}
