package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;

/**
 * The Water Tank: a shared, <b>inert</b> placeable component, and the Grass Spreader's tank cell.
 *
 * <p><b>It holds no water and it is not a Rain Collector.</b> It is a caged tote you craft from
 * plastic scrap, rebar and scrap metal, with no BlockEntity, no tank capability and nothing to fill.
 * The collector is built <em>from</em> it - a Copper Pipe over a tank - so the dependency runs that
 * way and not the other. Both machines share the tank because the tank is the primitive; a collector
 * already contains one.
 *
 * <p><b>This is corrected text.</b> It previously described the tank as "the Rain Collector
 * incorporated into the machine" and said forming drained a real collector you supplied. That was the
 * P2.4-R3 draft, superseded before the machine shipped and never true of the code; it survived here
 * long enough to produce wrong guidebook and pack copy twice (#202). The beat it promised - your first
 * machine becomes part of your second - is a good one and is not what this block does.
 *
 * <p>It extends {@link MultiblockDummyBlock} for the reason the Solar Panel does: standalone it
 * behaves like an ordinary block ({@code findCore} returns null and every override falls through),
 * and inside a formed machine it redirects break and use to the core so the machine is one object.
 */
public class WaterTankBlock extends MultiblockDummyBlock {

    public static final MapCodec<WaterTankBlock> CODEC = simpleCodec(WaterTankBlock::new);

    public WaterTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends WaterTankBlock> codec() {
        return CODEC;
    }
}
