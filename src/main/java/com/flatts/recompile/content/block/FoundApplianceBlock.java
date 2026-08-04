package com.flatts.recompile.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A discarded appliance pulled out of Bulky Waste: a plain cube that faces the player who placed it.
 *
 * <p><b>It is a block because the find is one.</b> The mattress set the precedent - a find is
 * something you can carry home and put down, not only an icon that exists to be consumed. Placing it
 * costs nothing here (no BlockEntity, no behaviour) and it is what earns the multi-face texture set:
 * a washing machine's porthole and a printer's paper slot only read as themselves if the front is a
 * distinct face.
 *
 * <p>Horizontally facing so that face points at the player. Without it, three quarters of placements
 * show a blank side panel and the object stops being recognisable - the same reason vanilla orients a
 * furnace.
 *
 * <p>Deliberately does <b>not</b> call {@code noOcclusion()}: these are boxes, and an occluding full
 * cube is the cheap case for face culling.
 *
 * <p>Shared rather than one class per appliance. The washing machine had this to itself and the
 * printer (#112) wanted the identical twenty lines, which is how two copies start.
 */
public class FoundApplianceBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<FoundApplianceBlock> CODEC = simpleCodec(FoundApplianceBlock::new);

    public FoundApplianceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends FoundApplianceBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Front toward the player, which is the opposite of the direction they are looking. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
