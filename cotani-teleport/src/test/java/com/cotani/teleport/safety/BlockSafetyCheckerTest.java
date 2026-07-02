package com.cotani.teleport.safety;

import static org.junit.jupiter.api.Assertions.*;

import com.cotani.teleport.api.SafeLocationOptions;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class BlockSafetyCheckerTest {

    private final SafeLocationOptions options = new SafeLocationOptions(2, 8, true, true, false);
    private final World world = org.mockito.Mockito.mock(World.class);

    @Test
    void isSafeReturnsFalseForNullWorld() {
        var loc = new Location(null, 0, 0, 0);
        assertFalse(BlockSafetyChecker.isSafe(loc, options));
    }

    @Test
    void isOutsideBoundsRejectsAboveMaxHeight() {
        org.mockito.Mockito.when(world.getMinHeight()).thenReturn(-64);
        org.mockito.Mockito.when(world.getMaxHeight()).thenReturn(320);
        var loc = new Location(world, 0, 320, 0);
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
