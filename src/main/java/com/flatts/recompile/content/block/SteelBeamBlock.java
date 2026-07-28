package com.flatts.recompile.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Steel I-Beam (demolition yard): an auto-connecting structural member, the Create Metal Girder / IE
 * scaffolding pattern built on vanilla {@link PipeBlock}. It joins any neighbouring Steel I-Beam on each of
 * its six faces, so a multipart blockstate assembles columns, girders, corners, T-joints and crosses from a
 * central node + a directional arm - a real see-through steel frame for the building husks (#49), not a
 * solid cube. `noOcclusion` (set on the block properties) is load-bearing: without it the slim model would
 * cull neighbours and punch holes in the world (the CLAUDE.md occlusion trap).
 *
 * <p>Cut only with the Cutting Torch (`requiresCorrectToolForDrops` + `#recompile:mineable/cutting_torch`);
 * drops raw iron in bulk. PipeBlock supplies the six {@code NORTH..DOWN} booleans, `PROPERTY_BY_DIRECTION`,
 * and the assembled collision/selection shape from the apothem.
 */
public class SteelBeamBlock extends PipeBlock {

    public static final MapCodec<SteelBeamBlock> CODEC = simpleCodec(SteelBeamBlock::new);

    public SteelBeamBlock(Properties properties) {
        super(0.1875F, properties);   // 3px apothem -> a ~6px slim core, arms fill out to each connected face
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
            .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    private static boolean isBeam(BlockState state) {
        return state.getBlock() instanceof SteelBeamBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = this.defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(direction),
                isBeam(level.getBlockState(pos.relative(direction))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTick,
            BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
            RandomSource random) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(direction), isBeam(neighborState));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }
}
