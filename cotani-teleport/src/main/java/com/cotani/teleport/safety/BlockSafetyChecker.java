package com.cotani.teleport.safety;

import com.cotani.teleport.api.SafeLocationOptions;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;

public final class BlockSafetyChecker {

    private BlockSafetyChecker() {}

    public static boolean isSafe(Location location, SafeLocationOptions options) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        if (options.respectWorldBorder() && !isInsideWorldBorder(world, location)) {
            return false;
        }
        if (isOutsideBounds(world, location)) {
            return false;
        }
        if (!isChunkLoaded(world, location)) {
            return false;
        }

        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        if (!feet.isPassable() || !head.isPassable()) {
            return false;
        }
        if (!ground.getType().isSolid()) {
            return false;
        }
        if (options.avoidLiquids() && isLiquid(feet, head, ground)) {
            return false;
        }
        return !options.avoidHazards() || !isHazard(feet.getType(), head.getType(), ground.getType());
    }

    private static boolean isInsideWorldBorder(World world, Location location) {
        WorldBorder border = world.getWorldBorder();
        return border.isInside(location);
    }

    private static boolean isChunkLoaded(World world, Location location) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    private static boolean isOutsideBounds(World world, Location location) {
        double y = location.getY();
        return y <= world.getMinHeight() || y >= world.getMaxHeight() - 2;
    }

    private static boolean isLiquid(Block... blocks) {
        for (Block block : blocks) {
            if (block.isLiquid()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHazard(Material... materials) {
        for (Material material : materials) {
            if (isHazardMaterial(material)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHazardMaterial(Material material) {
        return switch (material) {
            case LAVA, FIRE, SOUL_FIRE, CACTUS, MAGMA_BLOCK, SWEET_BERRY_BUSH, POWDER_SNOW -> true;
            default -> false;
        };
    }

    public static Location center(Location location) {
        return new Location(
                Objects.requireNonNull(location.getWorld(), "world"),
                location.getBlockX() + 0.5,
                location.getY(),
                location.getBlockZ() + 0.5,
                location.getYaw(),
                location.getPitch());
    }
}
