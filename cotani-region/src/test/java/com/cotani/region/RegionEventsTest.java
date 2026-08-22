package com.cotani.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.cotani.region.api.Region3D;
import com.cotani.region.api.RegionEnterEvent;
import com.cotani.region.api.RegionLeaveEvent;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class RegionEventsTest {

    @Test
    void shouldConstructEvents() {
        var player = mock(Player.class);
        var world = mock(World.class);
        var worldId = UUID.randomUUID();
        var region = Region3D.builder("r1", worldId).build();
        var loc1 = new Location(world, 0, 0, 0);
        var loc2 = new Location(world, 10, 0, 10);

        var enter = new RegionEnterEvent(player, region, loc1, loc2);
        assertEquals(player, enter.player());
        assertEquals(region, enter.region());
        assertEquals(loc1, enter.from());
        assertEquals(loc2, enter.to());

        var leave = new RegionLeaveEvent(player, region, loc1, loc2);
        assertEquals(player, leave.player());
        assertEquals(region, leave.region());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldEnforceNullSafety() {
        var player = mock(Player.class);
        var worldId = UUID.randomUUID();
        var region = Region3D.builder("r1", worldId).build();
        var loc = new Location(mock(World.class), 0, 0, 0);

        assertThrows(NullPointerException.class, () -> new RegionEnterEvent(null, region, loc, loc));
        assertThrows(NullPointerException.class, () -> new RegionLeaveEvent(player, null, loc, loc));
    }
}
