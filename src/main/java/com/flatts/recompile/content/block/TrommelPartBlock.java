package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockSkinnedBlock;
import com.mojang.serialization.MapCodec;

/**
 * A formed cell of the Trommel's stand: the frame under the drum, and the discharge chute.
 *
 * <p>One class for both, because they differ only in their model - bespoke art does not mean bespoke
 * behaviour, and both do the same thing here: redirect use and break to the master.
 *
 * <p>They carry the machine's facing so the chute's mouth points where the machine does. Without it a
 * chute is correct by accident on a north-facing build and wrong on the other three.
 */
public class TrommelPartBlock extends MultiblockSkinnedBlock {

    public static final MapCodec<TrommelPartBlock> CODEC = simpleCodec(TrommelPartBlock::new);

    public TrommelPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends TrommelPartBlock> codec() {
        return CODEC;
    }
}
