package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;

/**
 * The Grass Spreader's manifold: the metal frame a Pump becomes, sitting at the centre of the drip
 * ring with a copper spigot on each side.
 *
 * <p>It shows nothing itself - the water leaves through the spigots, so that is where the particles
 * live. This block is the plumbing that holds them, plus the mast that carries the solar panel above.
 */
public class GrassSpreaderFrameBlock extends MultiblockDummyBlock {

    public static final MapCodec<GrassSpreaderFrameBlock> CODEC = simpleCodec(GrassSpreaderFrameBlock::new);

    public GrassSpreaderFrameBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GrassSpreaderFrameBlock> codec() {
        return CODEC;
    }
}
