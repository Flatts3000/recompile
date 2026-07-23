package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;

/**
 * The Grass Spreader's tank cell: the Rain Collector incorporated into the machine.
 *
 * <p>You place a real collector and forming converts it to this, which is what makes "your first
 * machine becomes part of your second" literal rather than decorative. It reuses the collector's
 * tote model so it is visibly the thing you built.
 *
 * <p><b>Forming drains the collector.</b> Replacing the collector destroys its BlockEntity, so any
 * water it held is gone, and disband returns an empty one. That is deliberate, not an oversight -
 * the fiction is that the water is now plumbed into the sprinkler - but it does mean there is no
 * point filling a collector before building a spreader around it.
 */
public class GrassSpreaderTankBlock extends MultiblockDummyBlock {

    public static final MapCodec<GrassSpreaderTankBlock> CODEC = simpleCodec(GrassSpreaderTankBlock::new);

    public GrassSpreaderTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GrassSpreaderTankBlock> codec() {
        return CODEC;
    }
}
