package com.flatts.recompile.content.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shape the Scrap Network's hand-worked stations share: a bench top on four short legs.
 *
 * <p><b>One shape for three blocks, because they are meant to read as one thing.</b> The Scrap
 * Crafting Table, the Sorting Tarp and the Teardown Workbench are what a player lines up into a
 * cluster, and two of them were plain cubes while the third was a table - so a row of them looked
 * like three unrelated objects instead of a workstation. They now share
 * {@code block/workstation_table.json} and differ only in their textures.
 *
 * <p><b>The worktop spans the full 16 deliberately.</b> It used to run 1..15, leaving a pixel of
 * daylight each side and a two-pixel gap between neighbours, so adjacent stations never touched and
 * the cluster did not read as a continuous surface. Legs stay inset; only the top is flush.
 *
 * <p>A constant rather than a copy in each block: the model and the collision box have to agree, and
 * three hand-kept copies of the same numbers is how they stop agreeing. {@code WorkstationTests}
 * asserts all three blocks report this shape, so a block that quietly keeps a cube hitbox while
 * drawing a bench fails the build instead of just feeling wrong to stand on.
 */
public final class WorkstationTable {

    /** Mirrors the boxes in {@code assets/recompile/models/block/workstation_table.json}. */
    public static final VoxelShape SHAPE = Shapes.or(
        Block.box(0, 3, 0, 16, 13, 16),    // worktop, full width so a row of them touches
        Block.box(2, 0, 2, 5, 3, 5),       // legs
        Block.box(11, 0, 2, 14, 3, 5),
        Block.box(2, 0, 11, 5, 3, 14),
        Block.box(11, 0, 11, 14, 3, 14));

    private WorkstationTable() {
    }
}
