package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * One cell of the Trommel's drum: the perforated screen the machine is named for.
 *
 * <p><b>{@link #ACTIVE} is mirrored from the core rather than read from it</b>, the same one-property
 * mirror a vanilla furnace's {@code LIT} uses. A dummy cell has no BlockEntity and no cheap way to find
 * its master at render time, and without the mirror the animated texture is unreachable - which is
 * exactly how the Separator once shipped running models that nothing referenced, so its grinder never
 * appeared to turn.
 *
 * <p><b>{@link #CELL} is its place along the run</b>, 0 at the feed end. Four cells wearing the same
 * picture read as four blocks; a barrel needs its ends to look like ends.
 */
public class TrommelDrumBlock extends MultiblockDummyBlock {

    public static final MapCodec<TrommelDrumBlock> CODEC = simpleCodec(TrommelDrumBlock::new);

    /** Position along the drum, 0 at the feed end. */
    public static final IntegerProperty CELL =
        IntegerProperty.create("cell", 0, TrommelCoreBlock.LENGTH - 1);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Mirrors the core's ACTIVE, so the drum turns while the machine sorts. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public TrommelDrumBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(CELL, 0)
            .setValue(ACTIVE, false)
            .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends TrommelDrumBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CELL, FACING, ACTIVE);
    }
}
