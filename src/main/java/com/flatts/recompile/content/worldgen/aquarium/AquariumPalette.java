package com.flatts.recompile.content.worldgen.aquarium;

import com.flatts.recompile.content.block.SteelBeamBlock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the Municipal Aquarium is made of (spec 3.2). Nothing here is a new block, and that is the
 * precedent rather than a compromise: the cooling tower is Reinforced Concrete plus vanilla ground and
 * {@code SewerPalette} is entirely vanilla plus leachate.
 *
 * <p><b>Damage is expressed by absence, not by a block.</b> The spec's first draft said "cracked
 * glass", and vanilla has no such thing; a sheet with panes missing and a row of panes where the
 * sheet is half gone is what a ruin looks like at block resolution. {@link #TANK_GLASS} is reserved
 * for the guardian tank alone, so the darkest glass in the building marks the one tank still holding
 * water, readable from outside.
 */
public final class AquariumPalette {

    private AquariumPalette() {
    }

    /** The shell: the yard's own concrete, as the cooling tower is. */
    public static final BlockState SHELL = RCBlocks.REINFORCED_CONCRETE.get().defaultBlockState();
    /** Roof and gallery frame, the yard's steel. */
    public static final BlockState BEAM_Z = RCBlocks.STEEL_I_BEAM.get().defaultBlockState()
        .setValue(SteelBeamBlock.AXIS, Direction.Axis.Z);
    public static final BlockState BEAM_Y = RCBlocks.STEEL_I_BEAM.get().defaultBlockState()
        .setValue(SteelBeamBlock.AXIS, Direction.Axis.Y);

    /** Civic tiling and its two states of decay, exactly the sewer's three-course trick. */
    public static final BlockState WALL = Blocks.STONE_BRICKS.defaultBlockState();
    public static final BlockState AGED_WALL = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    public static final BlockState WET_WALL = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    public static final BlockState FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();

    /** The point of the building. */
    public static final BlockState CLADDING = Blocks.PRISMARINE_BRICKS.defaultBlockState();
    public static final BlockState CLADDING_BAND = Blocks.DARK_PRISMARINE.defaultBlockState();
    public static final BlockState CLADDING_PLAIN = Blocks.PRISMARINE.defaultBlockState();
    /** What an aquarium actually lights tanks with, and it is the same family. */
    public static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();

    /** Glazing: intact sheet, and the crack rule removes cells from it. */
    public static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    /** The guardian tank alone. */
    public static final BlockState TANK_GLASS = Blocks.TINTED_GLASS.defaultBlockState();

    /** {@code SewerPalette.GRATE} is the same block for the same reason. */
    public static final BlockState RAIL = Blocks.IRON_BARS.defaultBlockState();
    /** Brushable, and the same pair {@code SewerPalette} calls SILT and FINE_SILT. */
    public static final BlockState SILT = Blocks.SUSPICIOUS_GRAVEL.defaultBlockState();
    public static final BlockState FINE_SILT = Blocks.SUSPICIOUS_SAND.defaultBlockState();
    /** What a tank floor is: the exhibit bays stand their coral on it. */
    public static final BlockState BED = Blocks.SAND.defaultBlockState();
    public static final BlockState SPONGE = Blocks.SPONGE.defaultBlockState();
    public static final BlockState WET_SPONGE = Blocks.WET_SPONGE.defaultBlockState();

    /** Everywhere except the guardian tank. */
    public static final BlockState FLUID = RCBlocks.LEACHATE.get().defaultBlockState();
    /** The only water in the building (ruling 8.1). */
    public static final BlockState TANK_WATER = Blocks.WATER.defaultBlockState();

    public static final BlockState AGE = Blocks.COBWEB.defaultBlockState();
    public static final BlockState HOLLOW = Blocks.AIR.defaultBlockState();
    public static final BlockState PEDESTAL = RCBlocks.DISPLAY_PEDESTAL.get().defaultBlockState();

    /**
     * A deterministic three-way weathering pick, seeded from position rather than from the piece's
     * random: {@code postProcess} re-runs per overlapped chunk with a fresh RandomSource, and a wall
     * whose courses changed at a chunk seam would read as a generation bug rather than as decay.
     */
    public static BlockState weathered(BlockState fresh, BlockState aged, BlockState wet,
            int x, int y, int z) {
        int h = hash(x, y, z) % 9;
        return h < 5 ? fresh : h < 7 ? aged : wet;
    }

    /** True where a glass cell is missing; one in six, and only above anything that could leak. */
    public static boolean cracked(int x, int y, int z) {
        return hash(x, y, z) % 6 == 0;
    }

    public static int hash(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }
}
