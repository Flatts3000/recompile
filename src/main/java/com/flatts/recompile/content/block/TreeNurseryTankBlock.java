package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.multiblock.MultiblockSkinnedBlock;
import com.mojang.serialization.MapCodec;

/**
 * The Tree Nursery's formed water-tank cell: a <b>full block</b> clad in the machine's own panels so the
 * bottom-right of the wall reads as one machine, not a separate caged tote bolted on. A dummy - no item;
 * it disband-returns the Water Tank you placed.
 *
 * <p>This is the "a formed cell is a per-machine dummy, never the shared component restacked" rule
 * (CLAUDE.md): the Grass Spreader can reuse the caged Water Tank because it wants that industrial look,
 * but the nursery is a clad cabinet, so its tank gets its own integral cladding.
 *
 * <p><b>It wears the machine's skin</b> (2026-08-04): its half of the cabinet is cut from one image
 * spanning both bottom cells, so the frame, the seams and the grime run across the join instead of
 * restarting at it. Only the bottom row is skinned - the two Solar Panels above are the same block a
 * player places on its own and the Grass Spreader also uses, so they cannot carry a per-machine tile
 * without changing every panel in the world.
 */
public class TreeNurseryTankBlock extends MultiblockSkinnedBlock {

    public static final MapCodec<TreeNurseryTankBlock> CODEC = simpleCodec(TreeNurseryTankBlock::new);

    public TreeNurseryTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends TreeNurseryTankBlock> codec() {
        return CODEC;
    }
}
