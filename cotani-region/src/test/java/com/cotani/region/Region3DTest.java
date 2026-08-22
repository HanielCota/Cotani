package com.cotani.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.region.api.Region3D;
import com.cotani.region.api.RegionFlag;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class Region3DTest {

    @Test
    void shouldBuildRegionAndEvaluateContainment() {
        var world = mock(World.class);
        var worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);

        var region = Region3D.builder("spawn", worldId)
                .name("<gold>Spawn</gold>")
                .bounds(0, 50, 0, 100, 100, 100)
                .priority(10)
                .flag(RegionFlag.PVP, false)
                .build();

        assertEquals("spawn", region.id());
        assertEquals(worldId, region.worldId());
        assertEquals(10, region.priority());
        assertEquals(Optional.of(false), region.getFlag(RegionFlag.PVP));
        assertTrue(region.getFlag(RegionFlag.BLOCK_BREAK).isEmpty());

        // Test containment
        assertTrue(region.contains(new Location(world, 50, 70, 50)));
        assertTrue(region.contains(new Location(world, 0, 50, 0)));
        assertTrue(region.contains(new Location(world, 100, 100, 100)));

        assertFalse(region.contains(new Location(world, -1, 70, 50)));
        assertFalse(region.contains(new Location(world, 50, 49, 50)));
        assertFalse(region.contains(new Location(world, 50, 70, 101)));
    }

    @Test
    void shouldCalculateVolumeCorrectly() {
        var worldId = UUID.randomUUID();
        var region = Region3D.builder("box", worldId)
                .bounds(0, 0, 0, 9, 9, 9) // 10x10x10 = 1000 blocks
                .build();

        assertEquals(1000L, region.volume());
    }

    @Test
    void shouldFailWhenMinExceedsMax() {
        var worldId = UUID.randomUUID();
        assertThrows(
                IllegalArgumentException.class,
                () -> new Region3D(
                        "invalid",
                        net.kyori.adventure.text.Component.empty(),
                        worldId,
                        100,
                        0,
                        0,
                        0,
                        100,
                        100,
                        0,
                        java.util.Map.of(),
                        null,
                        null));
    }
}
