package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockDummyBlock;
import com.mojang.serialization.MapCodec;

/**
 * The Tree Nursery's formed water-tank cell: a <b>full block</b> clad in the machine's own panels so the
 * bottom-right of the wall reads as one machine, not a separate caged tote bolted on. A dummy - no item;
 * it disband-returns the Water Tank you placed.
 *
 * <p>This is the "a formed cell is a per-machine dummy, never the shared component restacked" rule
 * (CLAUDE.md): the Grass Spreader can reuse the caged Water Tank because it wants that industrial look,
 * but the nursery is a clad cabinet, so its tank gets its own integral cladding.
 */
public class TreeNurseryTankBlock extends MultiblockDummyBlock {

    public static final MapCodec<TreeNurseryTankBlock> CODEC = simpleCodec(TreeNurseryTankBlock::new);

    public TreeNurseryTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends TreeNurseryTankBlock> codec() {
        return CODEC;
    }
}
