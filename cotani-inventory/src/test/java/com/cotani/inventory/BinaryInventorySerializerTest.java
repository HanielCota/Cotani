package com.cotani.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.inventory.api.InventorySnapshot;
import com.cotani.inventory.api.PotionEffectSnapshot;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf("isPaperRuntimeAvailable")
class BinaryInventorySerializerTest {

    static boolean isPaperRuntimeAvailable() {
        try {
            ItemStack.empty();
            return true;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Test
    void shouldSerializeAndDeserializeSnapshot() {
        var serializer = CotaniInventories.binarySerializer();
        var playerId = UUID.randomUUID();

        var mainItem = new ItemStack(Material.DIAMOND_SWORD, 1);
        var armorItem = new ItemStack(Material.NETHERITE_CHESTPLATE, 1);
        var offHandItem = new ItemStack(Material.TOTEM_OF_UNDYING, 1);
        var enderItem = new ItemStack(Material.GOLDEN_APPLE, 64);

        var effect = new PotionEffectSnapshot(PotionEffectType.SPEED, 1200, 1, false, true, true);

        var snapshot = InventorySnapshot.builder(playerId)
                .version(2)
                .createdAt(1700000000000L)
                .mainContents(List.of(mainItem))
                .armorContents(List.of(armorItem))
                .offHand(offHandItem)
                .enderChestContents(List.of(enderItem))
                .experience(1500, 30, 0.75f)
                .health(18.5, 20.0)
                .food(19, 4.5f)
                .potionEffects(List.of(effect))
                .gameMode(GameMode.SURVIVAL)
                .flight(true, false)
                .build();

        byte[] serialized = serializer.serialize(snapshot);
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        var deserialized = serializer.deserialize(serialized);

        assertEquals(playerId, deserialized.playerId());
        assertEquals(2, deserialized.version());
        assertEquals(1700000000000L, deserialized.createdAt());
        assertEquals(1500, deserialized.totalExperience());
        assertEquals(30, deserialized.level());
        assertEquals(0.75f, deserialized.exp(), 0.001);
        assertEquals(18.5, deserialized.health(), 0.001);
        assertEquals(20.0, deserialized.maxHealth(), 0.001);
        assertEquals(19, deserialized.foodLevel());
        assertEquals(4.5f, deserialized.saturation(), 0.001);
        assertEquals(GameMode.SURVIVAL, deserialized.gameMode());
        assertTrue(deserialized.allowFlight());
        assertFalse(deserialized.flying());

        assertEquals(1, deserialized.mainContents().size());
        assertEquals(
                Material.DIAMOND_SWORD, deserialized.mainContents().getFirst().getType());

        assertEquals(1, deserialized.armorContents().size());
        assertEquals(
                Material.NETHERITE_CHESTPLATE,
                deserialized.armorContents().getFirst().getType());

        assertEquals(Material.TOTEM_OF_UNDYING, deserialized.offHand().getType());

        assertEquals(1, deserialized.enderChestContents().size());
        assertEquals(
                Material.GOLDEN_APPLE,
                deserialized.enderChestContents().getFirst().getType());
        assertEquals(64, deserialized.enderChestContents().getFirst().getAmount());

        assertEquals(1, deserialized.potionEffects().size());
        assertEquals(
                PotionEffectType.SPEED, deserialized.potionEffects().getFirst().type());
        assertEquals(1200, deserialized.potionEffects().getFirst().durationTicks());
        assertEquals(1, deserialized.potionEffects().getFirst().amplifier());
    }

    @Test
    void shouldSerializeAndDeserializeViaBase64() {
        var serializer = CotaniInventories.binarySerializer();
        var playerId = UUID.randomUUID();
        var snapshot = InventorySnapshot.empty(playerId);

        String base64 = serializer.toBase64(snapshot);
        assertNotNull(base64);
        assertFalse(base64.isEmpty());

        var deserialized = serializer.fromBase64(base64);
        assertEquals(playerId, deserialized.playerId());
    }
}
