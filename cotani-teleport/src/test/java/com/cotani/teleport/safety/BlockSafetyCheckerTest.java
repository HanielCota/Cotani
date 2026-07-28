package com.cotani.teleport.safety;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cotani.teleport.api.SafeLocationOptions;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

class BlockSafetyCheckerTest {

    private final SafeLocationOptions options = new SafeLocationOptions(2, 8, true, true, false);
    private final World world = mock(World.class);

    @Test
    void isSafeReturnsFalseForNullWorld() {
        var loc = new Location(null, 0, 0, 0);
        assertFalse(BlockSafetyChecker.isSafe(loc, options));
    }

    @Test
    void isOutsideBoundsRejectsAboveMaxHeight() {
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        var loc = new Location(world, 0, 320, 0);
        assertFalse(BlockSafetyChecker.isSafe(loc, options));
    }

    @Test
    void isOutsideBoundsRejectsAtMinHeightDueToGroundBlock() {
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        var loc = new Location(world, 0, -64, 0);
        assertFalse(BlockSafetyChecker.isSafe(loc, options));
    }

    @Test
    void isSafeRejectsHazardMaterials() {
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);

        Block feet = mock(Block.class);
        Block head = mock(Block.class);
        Block ground = mock(Block.class);

        when(feet.isPassable()).thenReturn(true);
        when(head.isPassable()).thenReturn(true);
        when(ground.isSolid()).thenReturn(true);
        when(ground.isPassable()).thenReturn(false);

        when(feet.isLiquid()).thenReturn(false);
        when(head.isLiquid()).thenReturn(false);
        when(ground.isLiquid()).thenReturn(false);

        when(feet.getType()).thenReturn(Material.WITHER_ROSE);
        when(head.getType()).thenReturn(Material.AIR);
        when(ground.getType()).thenReturn(Material.STONE);

        when(world.getBlockAt(0, 64, 0)).thenReturn(feet);
        when(world.getBlockAt(0, 65, 0)).thenReturn(head);
        when(world.getBlockAt(0, 63, 0)).thenReturn(ground);

        var loc = new Location(world, 0, 64, 0);
        assertFalse(BlockSafetyChecker.isSafe(loc, options));
    }

    @Test
    void centerPreservesYawAndPitch() {
        var loc = new Location(world, 10.2, 5.0, 20.7, 45.0f, 30.0f);
        var centered = BlockSafetyChecker.center(loc);
        assertEquals(10.5, centered.getX(), 0.001);
        assertEquals(5.0, centered.getY(), 0.001);
        assertEquals(20.5, centered.getZ(), 0.001);
        assertEquals(45.0f, centered.getYaw(), 0.001);
        assertEquals(30.0f, centered.getPitch(), 0.001);
    }

    @Test
    void centerThrowsForNullWorld() {
        var loc = new Location(null, 0, 0, 0);
        assertThrows(NullPointerException.class, () -> BlockSafetyChecker.center(loc));
    }
}
