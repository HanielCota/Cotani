package com.cotani.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.inventory.api.InventorySnapshot;
import com.cotani.testkit.StressTestSupport;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class InventorySnapshotStressTest {
    @Test
    void generatedSnapshotsPreservePlayerStateAndDefensiveCopies() {
        var material = mock(Material.class);
        when(material.isAir()).thenReturn(false);
        var item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.clone()).thenReturn(item);
        StressTestSupport.scenarios("inventory", "snapshot-boundaries", (context, random, player) -> {
            int level = random.nextInt(0, 10_001);
            int totalExperience = random.nextInt(level, Integer.MAX_VALUE);
            float progress = random.nextInt(0, 1_001) / 1_000.0F;
            double maxHealth = random.nextInt(1, 2_049);
            double health = Math.min(maxHealth, random.nextInt(0, 2_049));
            var mutableContents = new ArrayList<ItemStack>();
            mutableContents.add(item);

            var snapshot = new InventorySnapshot(
                    player.id(),
                    context.iteration(),
                    Math.max(0L, random.nextLong(0L, Long.MAX_VALUE)),
                    mutableContents,
                    List.of(),
                    item,
                    List.of(),
                    totalExperience,
                    level,
                    progress,
                    health,
                    maxHealth,
                    random.nextInt(0, 21),
                    random.nextInt(0, 21),
                    List.of(),
                    GameMode.SURVIVAL,
                    context.iteration() % 2 == 0,
                    context.iteration() % 4 == 0);
            mutableContents.clear();

            assertEquals(player.id(), snapshot.playerId(), context::description);
            assertEquals(level, snapshot.level(), context::description);
            assertEquals(totalExperience, snapshot.totalExperience(), context::description);
            assertEquals(progress, snapshot.exp(), context::description);
            assertEquals(health, snapshot.health(), context::description);
            assertEquals(maxHealth, snapshot.maxHealth(), context::description);
            assertNotSame(mutableContents, snapshot.mainContents(), context::description);
            assertNotSame(snapshot.mainContents(), snapshot.armorContents(), context::description);
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> snapshot.mainContents().clear());
        });
    }
}
