package com.flatts.recompile.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A discarded washing machine: the second Bulky Waste find, and the only source of the Pump.
 *
 * <p><b>It is a block because the find is one.</b> The mattress set the precedent - a find is
 * something you can carry home and put down, not only an icon that exists to be consumed. Placing it
 * costs nothing here (no BlockEntity, no behaviour) and it is what earns the four-face texture set:
 * the porthole door only reads as a washing machine if the front is a distinct face.
 *
 * <p>Horizontally facing so the door faces the player who placed it. Without that, three quarters of
 * placements show a blank enamel side and the object stops being recognisable - the same reason
 * vanilla orients a furnace.
 *
 * <p>Note this is a full cube, so it deliberately does <b>not</b> call {@code noOcclusion()}: a
 * washing machine is a box, and an occluding full cube is the cheap case for face culling.
 */
public class WashingMachineBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<WashingMachineBlock> CODEC = simpleCodec(WashingMachineBlock::new);

    public WashingMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends WashingMachineBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Door toward the player, which is the opposite of the direction they are looking. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
