package com.cotani.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.npc.api.Npc;
import com.cotani.npc.impl.NpcSpatialIndex;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NpcSpatialIndexTest {

    private NpcSpatialIndex spatialIndex;
    private World world;
    private UUID worldId;

    @BeforeEach
    void setUp() {
        spatialIndex = new NpcSpatialIndex();
        world = mock(World.class);
        worldId = UUID.randomUUID();
        when(world.getUID()).thenReturn(worldId);
    }

    @Test
    void shouldIndexAndRetrieveNearbyNpcs() {
        var npc1 = Npc.builder()
                .id(UUID.randomUUID())
                .name("NPC1")
                .location(new Location(world, 0, 64, 0)) // Chunk (0, 0)
                .build();

        var npc2 = Npc.builder()
                .id(UUID.randomUUID())
                .name("NPC2")
                .location(new Location(world, 1000, 64, 1000)) // Far away Chunk (62, 62)
                .build();

        spatialIndex.add(npc1);
        spatialIndex.add(npc2);

        // Query radius 2 chunks around (0, 0)
        var nearby = spatialIndex.getNearby(worldId, 0, 0, 2);
        assertEquals(1, nearby.size());
        assertEquals(npc1, nearby.getFirst());

        // Query radius around far NPC
        var farNearby = spatialIndex.getNearby(worldId, 62, 62, 2);
        assertEquals(1, farNearby.size());
        assertEquals(npc2, farNearby.getFirst());
    }

    @Test
    void shouldRemoveAndClearIndex() {
        var npc = Npc.builder()
                .name("TestNPC")
                .location(new Location(world, 16, 64, 16))
                .build();

        spatialIndex.add(npc);
        var nearbyBefore = spatialIndex.getNearby(worldId, 1, 1, 1);
        assertEquals(1, nearbyBefore.size());

        spatialIndex.remove(npc.id());
        var nearbyAfter = spatialIndex.getNearby(worldId, 1, 1, 1);
        assertTrue(nearbyAfter.isEmpty());
    }
}
