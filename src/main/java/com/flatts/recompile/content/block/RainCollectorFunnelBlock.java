package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Rain Collector's tarp funnel: the upper cell of the formed machine, and the part that reads
 * as the catch.
 *
 * <p>It is the machine's <b>bespoke formed appearance</b> - a Machine Frame goes in, a tarp funnel
 * comes out. Thematically that is exactly right: a tarp is stretched over a frame. Visually it is
 * the vanilla hopper's geometry and its {@code hopper_inside} detail recoloured to tarp blue, which
 * is why it reads as a funnel at all - that shape lives in the texture, not the model.
 *
 * <p>Never crafted and never held: it has no item, and exists only inside a formed collector.
 * Breaking it drops a Machine Frame (its loot table) and takes the machine down with it, which
 * {@link MultiblockDummyBlock} handles.
 */
public class RainCollectorFunnelBlock extends MultiblockDummyBlock {

    public static final MapCodec<RainCollectorFunnelBlock> CODEC = simpleCodec(RainCollectorFunnelBlock::new);

    /** The funnel's catch basin - full width at the rim, matching the hopper silhouette. */
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);

    public RainCollectorFunnelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends RainCollectorFunnelBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
