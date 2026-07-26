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
 * The dump mushroom (design P1.9): a first-party forageable mushroom that grows on
 * vanilla {@code minecraft:mycelium} (and dirt) in any light - its own {@link #mayPlaceOn}
 * skips the vanilla mushroom light check. Worldgen places it; the player forages it by
 * breaking it, which drops the edible {@code dump_mushroom} item.
 *
 * <p>That item is a {@code BlockItem} (see {@code RCItems.DUMP_MUSHROOM}), so the mushroom
 * has parity with vanilla mushrooms: it can be replanted on mycelium or dirt, which makes
 * foraging a renewable loop rather than a one-way strip-mine.
 */
public class DumpMushroomBlock extends VegetationBlock {

    public static final MapCodec<DumpMushroomBlock> CODEC = simpleCodec(DumpMushroomBlock::new);

    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 8, 11);

    public DumpMushroomBlock(Properties properties) {
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
        return state.is(Blocks.MYCELIUM) || state.is(BlockTags.DIRT);
    }
}
