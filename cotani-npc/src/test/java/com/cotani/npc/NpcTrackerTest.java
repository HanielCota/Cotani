package com.cotani.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.npc.api.Npc;
import com.cotani.npc.impl.NpcRenderer;
import com.cotani.npc.impl.NpcTracker;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NpcTrackerTest {

    private NpcRenderer renderer;
    private NpcTracker tracker;
    private World world;

    @BeforeEach
    void setUp() {
        renderer = new NpcRenderer();
        tracker = new NpcTracker(renderer);
        world = mock(World.class);
    }

    @Test
    void shouldCalculateCorrectLookAtTrigonometry() {
        var npcLoc = new Location(world, 0, 64, 0);
        var playerEyeLoc = new Location(world, 0, 64 + 1.62, 10); // 10 blocks South

        var rotation = NpcTracker.calculateLookAt(npcLoc, playerEyeLoc);
        // Facing South: Yaw 0 deg, Pitch 0 deg
        assertEquals(0f, rotation[0], 0.1f);
        assertEquals(0f, rotation[1], 0.1f);
    }

    @Test
    void shouldTrackViewerWithinViewDistance() {
        var viewer = mock(Player.class);
        var viewerLoc = new Location(world, 10, 64, 10);
        var eyeLoc = new Location(world, 10, 65.62, 10);
        when(viewer.isOnline()).thenReturn(true);
        when(viewer.getLocation()).thenReturn(viewerLoc);
        when(viewer.getEyeLocation()).thenReturn(eyeLoc);
        when(viewer.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        var npc = Npc.builder()
                .location(new Location(world, 12, 64, 12)) // ~2.8 blocks away
                .name("TestNPC")
                .viewDistance(32.0)
                .build();

        tracker.trackViewer(viewer, List.of(npc));
        assertTrue(renderer.isVisibleTo(viewer, npc.id()));
    }
}
