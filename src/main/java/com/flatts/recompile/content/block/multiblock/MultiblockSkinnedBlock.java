package com.flatts.recompile.content.block.multiblock;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A formed cell that wears its share of <b>one texture stretched over the whole machine</b>
 * (design: {@code docs/multiblock_system_spec.md}).
 *
 * <p><b>The problem this solves.</b> A machine built from one repeating 16px panel reads as a grid of
 * tiles, not as a machine - and the stronger the panel's art, the worse it gets, because the eye counts
 * the repeats. Six identical tiles across a flank is a wall, not a housing. No amount of prompt work
 * fixes it: the failure is the repetition itself.
 *
 * <p><b>The fix.</b> The machine's whole surface is authored as one image per face, cut into 16px tiles
 * at build time, and each cell shows the tile belonging to its own position. Nothing repeats, so the
 * seams stop being visible and the machine becomes a single designed object. The Separator's 2x2
 * grinding bay already worked this way and was the proof; this generalises it to every face of every
 * machine.
 *
 * <p>{@link #CELL} is that position, stamped by the core at assembly and derived from the blueprint
 * <b>offset</b> rather than a list index, so reordering {@code createBlueprint} cannot scramble the
 * skin. {@link #FACING} is the machine's facing, applied as a model rotation - which turns the whole
 * skin together and is exactly why the cell index can be taken from the unrotated offset.
 *
 * <p>The ceiling of {@value #MAX_CELLS} is a state-count budget, not a structural limit. Every machine
 * shipped today fits with room to spare (the Separator is the largest at twelve), and a machine that
 * outgrows it wants a different approach than a blockstate per cell anyway.
 */
public class MultiblockSkinnedBlock extends MultiblockDummyBlock {

    /** How many cells a skinned machine may have. See the class note. */
    public static final int MAX_CELLS = 16;

    /** Where this cell sits in the machine, from {@link Multiblock#cellIndex}. */
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, MAX_CELLS - 1);

    /** The machine's facing, so a sided model points the way the machine was built. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public MultiblockSkinnedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(CELL, 0)
            .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CELL, FACING);
    }
}
