package com.cotani.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.cotani.npc.api.Npc;
import com.cotani.npc.api.NpcInteractEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

class NpcInteractEventTest {

    @Test
    void shouldConstructEventWithValidParameters() {
        var player = mock(Player.class);
        var world = mock(World.class);
        var npc = Npc.builder()
                .location(new Location(world, 0, 64, 0))
                .name("Npc")
                .build();

        var event = new NpcInteractEvent(player, npc, EquipmentSlot.HAND, NpcInteractEvent.Action.RIGHT_CLICK);

        assertEquals(player, event.player());
        assertEquals(npc, event.npc());
        assertEquals(EquipmentSlot.HAND, event.hand());
        assertEquals(NpcInteractEvent.Action.RIGHT_CLICK, event.action());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldEnforceNullSafety() {
        var player = mock(Player.class);
        var world = mock(World.class);
        var npc = Npc.builder()
                .location(new Location(world, 0, 64, 0))
                .name("Npc")
                .build();

        assertThrows(
                NullPointerException.class,
                () -> new NpcInteractEvent(null, npc, EquipmentSlot.HAND, NpcInteractEvent.Action.RIGHT_CLICK));
        assertThrows(
                NullPointerException.class,
                () -> new NpcInteractEvent(player, null, EquipmentSlot.HAND, NpcInteractEvent.Action.RIGHT_CLICK));
        assertThrows(
                NullPointerException.class,
                () -> new NpcInteractEvent(player, npc, null, NpcInteractEvent.Action.RIGHT_CLICK));
        assertThrows(NullPointerException.class, () -> new NpcInteractEvent(player, npc, EquipmentSlot.HAND, null));
    }
}
