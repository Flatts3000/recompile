package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockSkinnedBlock;
import com.mojang.serialization.MapCodec;

/**
 * A formed cell of the Pulverizer: sealed housing, and nothing else.
 *
 * <p><b>One block for all seven cells, which is the machine's whole identity.</b> The Separator shows
 * you its bay because a shredder's throat is open; the Trommel shows you its screen because a drum is
 * perforated. A hammer mill is a closed steel box containing something violent, and the one thing you
 * cannot do is see in. Giving a cell a window would be a nicer picture and a worse machine.
 */
public class PulverizerPartBlock extends MultiblockSkinnedBlock {

    public static final MapCodec<PulverizerPartBlock> CODEC = simpleCodec(PulverizerPartBlock::new);

    public PulverizerPartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PulverizerPartBlock> codec() {
        return CODEC;
    }
}
