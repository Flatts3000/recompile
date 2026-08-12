package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.WorkstationTable;
import com.flatts.recompile.registry.RCBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The three hand-worked stations look and behave like one piece of furniture.
 *
 * <p>The Scrap Crafting Table, the Sorting Tarp and the Teardown Workbench are what a player lines
 * up into a Scrap Network cluster, and they used to be two cubes and a table - so a row of them read
 * as three unrelated objects rather than a workstation. They now share one model and one shape.
 *
 * <p><b>Two things a model file cannot enforce on its own,</b> and both fail quietly:
 *
 * <ul>
 *   <li>A block can draw a bench and keep a cube hitbox. Nothing errors; you just stand three
 *       pixels above the surface you can see, and it reads as the game being slightly broken.
 *   <li>A non-cube model on a block without {@code noOcclusion()} culls its neighbour's face, so you
 *       see straight through the ground beside it. That one looks like a rendering glitch with no
 *       cause, which is exactly why it costs an afternoon.
 * </ul>
 */
final class WorkstationTests {

    /** The blocks that must agree. Adding a fourth station means adding it here. */
    private static List<Block> stations() {
        return List.of(
            RCBlocks.SCRAP_CRAFTING_TABLE.get(),
            RCBlocks.SORTING_TARP.get(),
            RCBlocks.RECOMPILE_WORKBENCH.get());
    }

    private WorkstationTests() {
    }

    static void register() {
        RCGameTests.test("workstations_share_one_bench_shape", 20, helper -> {
            BlockPos spot = new BlockPos(1, 1, 1);
            List<String> wrong = new ArrayList<>();
            for (Block block : stations()) {
                helper.setBlock(spot, block);
                BlockState state = helper.getLevel().getBlockState(helper.absolutePos(spot));
                if (!state.getShape(helper.getLevel(), helper.absolutePos(spot))
                        .equals(WorkstationTable.SHAPE)) {
                    wrong.add(String.valueOf(block));
                }
            }
            helper.assertTrue(wrong.isEmpty(),
                "these stations draw the shared bench but do not use its collision shape, so you "
                    + "stand on a surface that is not where it looks: " + wrong);

            // The bench must be flush, or a row of stations never touches and the cluster does not
            // read as one surface - which is the whole reason the shape was widened.
            helper.assertTrue(WorkstationTable.SHAPE.min(net.minecraft.core.Direction.Axis.X) == 0.0
                    && WorkstationTable.SHAPE.max(net.minecraft.core.Direction.Axis.X) == 1.0,
                "the worktop must span the full block on X so neighbouring stations touch, got "
                    + WorkstationTable.SHAPE.min(net.minecraft.core.Direction.Axis.X) + ".."
                    + WorkstationTable.SHAPE.max(net.minecraft.core.Direction.Axis.X));
            helper.succeed();
        });

        RCGameTests.test("workstations_do_not_occlude", 20, helper -> {
            BlockPos spot = new BlockPos(1, 1, 1);
            List<String> solid = new ArrayList<>();
            for (Block block : stations()) {
                helper.setBlock(spot, block);
                BlockState state = helper.getLevel().getBlockState(helper.absolutePos(spot));
                if (state.canOcclude()) {
                    solid.add(String.valueOf(block));
                }
            }
            helper.assertTrue(solid.isEmpty(),
                "these draw a bench but still occlude like a full cube, so the game culls the "
                    + "neighbouring block's face and you see through the ground next to them. Add "
                    + "noOcclusion() to their Properties: " + solid);
            helper.succeed();
        });
    }
}
