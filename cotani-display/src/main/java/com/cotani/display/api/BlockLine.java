package com.cotani.display.api;

import java.util.Objects;
import org.bukkit.block.data.BlockData;

/**
 * An immutable block display line rendered via a {@link org.bukkit.entity.BlockDisplay} entity.
 */
public record BlockLine(
        BlockData blockData, DisplayBillboard billboard, float scale, float viewRange, double heightOffset)
        implements HologramLine {

    public static final double DEFAULT_BLOCK_HEIGHT_OFFSET = 0.6;

    public BlockLine {
        Objects.requireNonNull(blockData, "blockData cannot be null");
        Objects.requireNonNull(billboard, "billboard cannot be null");
    }

    /**
     * Creates a block display line.
     *
     * @param blockData the block data
     * @return the created block line
     */
    public static BlockLine of(BlockData blockData) {
        return new BlockLine(blockData, DisplayBillboard.FIXED, 0.5f, 1.0f, DEFAULT_BLOCK_HEIGHT_OFFSET);
    }

    /**
     * Creates a block display line with specific scale and billboard.
     *
     * @param blockData the block data
     * @param billboard the billboard mode
     * @param scale the scaling factor
     * @return the created block line
     */
    public static BlockLine of(BlockData blockData, DisplayBillboard billboard, float scale) {
        return new BlockLine(blockData, billboard, scale, 1.0f, DEFAULT_BLOCK_HEIGHT_OFFSET);
    }

    /**
     * Returns a copy with the updated block data.
     *
     * @param newBlockData the new block data
     * @return the updated BlockLine
     */
    public BlockLine withBlockData(BlockData newBlockData) {
        Objects.requireNonNull(newBlockData, "newBlockData cannot be null");
        return new BlockLine(newBlockData, billboard, scale, viewRange, heightOffset);
    }

    /**
     * Returns a copy with the specified height offset.
     *
     * @param offset the vertical offset in blocks
     * @return the updated BlockLine
     */
    public BlockLine withHeightOffset(double offset) {
        return new BlockLine(blockData, billboard, scale, viewRange, offset);
    }
}
