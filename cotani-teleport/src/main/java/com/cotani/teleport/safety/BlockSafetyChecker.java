package com.cotani.teleport.safety;

import com.cotani.teleport.api.SafeLocationOptions;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        Block ground = world.getBlockAt(x, y - 1, z);

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
        return world.getWorldBorder().isInside(location);
    }

    private static boolean isChunkLoaded(World world, Location location) {
        return world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private static boolean isOutsideBounds(World world, Location location) {
        double y = location.getY();
        return y < world.getMinHeight() || y + 1 >= world.getMaxHeight();
    }

    private static boolean isLiquid(Block feet, Block head, Block ground) {
        return feet.isLiquid() || head.isLiquid() || ground.isLiquid();
    }

    private static boolean isHazard(Material feet, Material head, Material ground) {
        return isHazardMaterial(feet) || isHazardMaterial(head) || isHazardMaterial(ground);
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
