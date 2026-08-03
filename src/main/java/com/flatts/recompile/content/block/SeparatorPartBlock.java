package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;

/**
 * A formed cell of the Separator: chamber, housing or chute
 * ({@code docs/separator_model_spec.md}).
 *
 * <p>One class for all three, because they differ only in their model. A formed cell is a <b>bespoke
 * per-machine block</b> rather than the component's model restacked, which is what lets the machine be
 * a designed object; but bespoke <i>art</i> does not mean bespoke <i>behaviour</i>, and all three do
 * the same thing here: redirect use and break to the master.
 *
 * <p>The chamber's motion is an animated texture on its top face, driven by nothing at all - vanilla
 * cycles it. Only the core carries the running state, and only so the model can swap.
 */
public class SeparatorPartBlock extends MultiblockDummyBlock {

    public static final MapCodec<SeparatorPartBlock> CODEC = simpleCodec(SeparatorPartBlock::new);

    public SeparatorPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends SeparatorPartBlock> codec() {
        return CODEC;
    }
}
