package com.flatts.recompile.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A dump-friendly pioneer plant (Vegetation tier, reclamation rung 2): the hardy weeds Fertilizer
 * scatters onto reclaimed grass. A plain cross-model plant - no collision, instant-break, cutout - that
 * survives on ordinary plantable ground (grass or dirt). The "reclaimed only" rule lives at
 * <em>placement</em> (Fertilizer no-ops off grass), not at survival, so a plant does not pop off if the
 * grass under it is contested (it is stripped first as {@code frontier_cover}).
 *
 * <p>Unlike {@link DumpMushroomBlock} these have a block-item, so a plant broken with shears can be
 * replaced by hand; the default {@code getCloneItemStack} already returns that item.
 */
public class DumpPlantBlock extends VegetationBlock {

    public static final MapCodec<DumpPlantBlock> CODEC = simpleCodec(DumpPlantBlock::new);

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

    public DumpPlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends VegetationBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(BlockTags.DIRT);
    }
}
