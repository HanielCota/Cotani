package com.cotani.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.npc.api.Npc;
import com.cotani.npc.api.NpcEquipment;
import com.cotani.npc.api.NpcPose;
import com.cotani.npc.api.NpcSkin;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class NpcTest {

    @Test
    void shouldBuildNpcWithValidProperties() {
        var world = mock(World.class);
        var location = new Location(world, 100, 64, 200, 90f, 0f);
        var id = UUID.randomUUID();
        var skin = NpcSkin.of("texture_val", "sig_val");
        var helmet = mock(ItemStack.class);
        when(helmet.clone()).thenReturn(helmet);

        var equipment = NpcEquipment.builder().helmet(helmet).build();

        var npc = Npc.builder()
                .id(id)
                .name("<gold>Shopkeeper</gold>")
                .location(location)
                .skin(skin)
                .equipment(equipment)
                .pose(NpcPose.STANDING)
                .lookAtPlayer(true)
                .viewDistance(32.0)
                .build();

        assertEquals(id, npc.id());
        assertEquals(location, npc.location());
        assertEquals(skin, npc.skin());
        assertEquals(helmet, npc.equipment().helmet());
        assertEquals(NpcPose.STANDING, npc.pose());
        assertTrue(npc.lookAtPlayer());
        assertEquals(32.0, npc.viewDistance());
    }

    @Test
    void shouldFailWhenBuildingWithoutLocation() {
        var builder = Npc.builder().name("Test");
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void shouldCreateNpcSkinCorrectly() {
        var skin = NpcSkin.of("abc", "def");
        assertEquals("abc", skin.value());
        assertEquals("def", skin.signature());
        assertTrue(skin.isPresent());

        var emptySkin = NpcSkin.EMPTY;
        assertFalse(emptySkin.isPresent());
    }

    @Test
    void shouldBuildNpcEquipmentCorrectly() {
        var sword = mock(ItemStack.class);
        when(sword.clone()).thenReturn(sword);

        var eq = NpcEquipment.builder().mainHand(sword).build();

        assertEquals(sword, eq.mainHand());
        assertNull(eq.offHand());
        assertNull(eq.helmet());
    }

    @Test
    void shouldSupportToBuilderRoundTrip() {
        var world = mock(World.class);
        var location = new Location(world, 0, 100, 0);
        var npc = Npc.builder()
                .name(Component.text("Original"))
                .location(location)
                .build();

        var modified = npc.toBuilder().name(Component.text("Modified")).build();

        assertEquals(Component.text("Modified"), modified.displayName());
        assertEquals(npc.id(), modified.id());
    }
}
