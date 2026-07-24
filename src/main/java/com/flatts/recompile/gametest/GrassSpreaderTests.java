package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.GrassSpreaderCoreBlock;
import com.flatts.recompile.content.block.GrassSpreaderCoreBlock.Outcome;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * GameTests for the Grass Spreader (design P2.4-R3): reclamation rung 1, a sprinkler that converts
 * dead ground to grass within a radius.
 *
 * <p>The structure tests belong to the multiblock framework and are covered by the Rain Collector's
 * suite; these are the machine's own - the conversion rules, and the two behaviours that would
 * otherwise fail silently: it must not run while unformed, and it must always take the *nearest*
 * ground first.
 */
final class GrassSpreaderTests {

    /** The core sits here; the tower runs up from it and the ground it waters is around it. */
    private static final BlockPos CORE = new BlockPos(1, 1, 1);

    private GrassSpreaderTests() {
    }

    /** Build the whole tower the way the game does, and form it. */
    private static void formSpreader(GameTestHelper helper) {
        formSpreaderAt(helper, CORE);
    }

    /** Same, at an arbitrary core position - for tests that need headroom above the machine. */
    private static void formSpreaderAt(GameTestHelper helper, BlockPos core) {
        helper.setBlock(core, RCBlocks.GRASS_SPREADER.get());
        helper.setBlock(core.above(), RCBlocks.WATER_TANK.get());
        helper.setBlock(core.above(2), RCBlocks.PUMP.get());
        // the drip ring - four copper pipes around the manifold
        BlockPos manifold = core.above(2);
        helper.setBlock(manifold.east(), RCBlocks.COPPER_PIPE.get());
        helper.setBlock(manifold.west(), RCBlocks.COPPER_PIPE.get());
        helper.setBlock(manifold.south(), RCBlocks.COPPER_PIPE.get());
        helper.setBlock(manifold.north(), RCBlocks.COPPER_PIPE.get());
        helper.setBlock(core.above(3), RCBlocks.SOLAR_PANEL.get());
        MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core));
    }

    static void register() {
        // The base case: dead ground becomes grass. Straight to grass, never stopping at dirt -
        // an intermediate would let vanilla's own grass spread finish the job for free, which is
        // exactly what "nothing renews on its own" forbids.
        RCGameTests.test("grass_spreader_converts_coarse_dirt_to_grass", 40, helper -> {
            formSpreader(helper);
            BlockPos ground = new BlockPos(3, 1, 1);
            helper.setBlock(ground, Blocks.COARSE_DIRT);

            Outcome outcome = GrassSpreaderCoreBlock.spreadOnce(
                helper.getLevel(), helper.absolutePos(CORE));

            helper.assertTrue(outcome == Outcome.SPREAD, "it must convert ground, got " + outcome);
            helper.assertTrue(helper.getBlockState(ground).is(Blocks.GRASS_BLOCK),
                "coarse dirt must become grass in one step, got " + helper.getBlockState(ground));
            helper.succeed();
        });

        // Plain dirt is a target too - it is what a half-done conversion would leave, and vanilla
        // CAN spread grass onto it, so skipping it would hand the player free healing.
        RCGameTests.test("grass_spreader_converts_plain_dirt", 40, helper -> {
            formSpreader(helper);
            BlockPos ground = new BlockPos(3, 1, 1);
            helper.setBlock(ground, Blocks.DIRT);

            GrassSpreaderCoreBlock.spreadOnce(helper.getLevel(), helper.absolutePos(CORE));

            helper.assertTrue(helper.getBlockState(ground).is(Blocks.GRASS_BLOCK),
                "plain dirt must convert too - vanilla can spread onto it, so leaving it is a loophole");
            helper.succeed();
        });

        // Nearest-first. This is the rule the whole design leans on: it makes green read as growing
        // outward, AND it means ground the frontier just took back (which is closer) is repaired
        // before new ground is broken. If it ever picked arbitrarily, both behaviours vanish and
        // nothing else would fail.
        RCGameTests.test("grass_spreader_takes_the_nearest_ground_first", 40, helper -> {
            formSpreader(helper);
            BlockPos near = new BlockPos(2, 1, 1);
            BlockPos far = new BlockPos(4, 1, 1);
            helper.setBlock(near, Blocks.COARSE_DIRT);
            helper.setBlock(far, Blocks.COARSE_DIRT);

            GrassSpreaderCoreBlock.spreadOnce(helper.getLevel(), helper.absolutePos(CORE));

            helper.assertTrue(helper.getBlockState(near).is(Blocks.GRASS_BLOCK),
                "the nearer block must be converted first");
            helper.assertTrue(helper.getBlockState(far).is(Blocks.COARSE_DIRT),
                "the farther block must wait its turn");
            helper.succeed();
        });

        // Mycelium is the dump-mushroom substrate and the P1.9 forage economy. Paving it would
        // quietly delete the only renewable food in the world - the same carve-out encroachment makes.
        RCGameTests.test("grass_spreader_spares_mycelium", 40, helper -> {
            formSpreader(helper);
            BlockPos ground = new BlockPos(3, 1, 1);
            helper.setBlock(ground, Blocks.MYCELIUM);

            GrassSpreaderCoreBlock.spreadOnce(helper.getLevel(), helper.absolutePos(CORE));

            helper.assertTrue(helper.getBlockState(ground).is(Blocks.MYCELIUM),
                "mycelium must be spared so foraging survives");
            helper.succeed();
        });

        // Only the tag's blocks convert, so nothing the player built is ever paved over.
        RCGameTests.test("grass_spreader_never_converts_built_blocks", 40, helper -> {
            formSpreader(helper);
            BlockPos ground = new BlockPos(3, 1, 1);
            helper.setBlock(ground, RCBlocks.PRESSED_JUNK_BLOCK.get());

            GrassSpreaderCoreBlock.spreadOnce(helper.getLevel(), helper.absolutePos(CORE));

            helper.assertTrue(helper.getBlockState(ground).is(RCBlocks.PRESSED_JUNK_BLOCK.get()),
                "a built block must never be converted");
            helper.succeed();
        });

        // Roofed ground is skipped. Without this the machine converts it, vanilla immediately kills
        // the grass back to dirt, and the spreader spends its entire budget on that one block
        // forever - a livelock that would look like "the spreader stopped working".
        RCGameTests.test("grass_spreader_skips_roofed_ground", 40, helper -> {
            formSpreader(helper);
            BlockPos ground = new BlockPos(3, 1, 1);
            helper.setBlock(ground, Blocks.COARSE_DIRT);
            helper.setBlock(ground.above(), RCBlocks.PRESSED_JUNK_BLOCK.get());   // a roof

            GrassSpreaderCoreBlock.spreadOnce(helper.getLevel(), helper.absolutePos(CORE));

            helper.assertTrue(helper.getBlockState(ground).is(Blocks.COARSE_DIRT),
                "roofed ground must be skipped - grass could not survive there");
            helper.succeed();
        });

        // An unformed core does nothing. The tower is the machine; a bare core is a crate.
        RCGameTests.test("grass_spreader_unformed_does_nothing", 40, helper -> {
            helper.setBlock(CORE, RCBlocks.GRASS_SPREADER.get());   // no tower
            BlockPos ground = new BlockPos(3, 1, 1);
            helper.setBlock(ground, Blocks.COARSE_DIRT);

            Outcome outcome = GrassSpreaderCoreBlock.spreadOnce(
                helper.getLevel(), helper.absolutePos(CORE));

            helper.assertTrue(outcome == Outcome.UNFORMED,
                "an unformed core must not run, got " + outcome);
            helper.assertTrue(helper.getBlockState(ground).is(Blocks.COARSE_DIRT),
                "an unformed core must not convert anything");
            helper.succeed();
        });

        // The vertical bound: ground too far above or below is out of reach, so a spreader cannot
        // water the top of a cliff or the bottom of a pit it happens to stand beside.
        //
        // Note this asserts the *specific* block is untouched rather than asserting an IDLE
        // outcome. GameTest plots share one world and the radius reaches past a 5x5x5 plot into
        // neighbouring ones, so "nothing anywhere in range is eligible" is not a claim this suite
        // can make - but "this block was not converted" holds no matter what else is out there.
        RCGameTests.test("grass_spreader_respects_vertical_tolerance", 40, helper -> {
            BlockPos low = new BlockPos(1, 0, 1);
            formSpreaderAt(helper, low);
            // tolerance defaults to 3, so +4 is out of band
            BlockPos tooHigh = new BlockPos(3, 4, 1);
            helper.setBlock(tooHigh, Blocks.COARSE_DIRT);

            GrassSpreaderCoreBlock.spreadOnce(helper.getLevel(), helper.absolutePos(low));

            helper.assertTrue(helper.getBlockState(tooHigh).is(Blocks.COARSE_DIRT),
                "ground beyond the vertical tolerance must be out of reach");
            helper.succeed();
        });

        // The whole tower assembles from real components and comes apart returning each one once.
        RCGameTests.test("grass_spreader_forms_and_disbands_cleanly", 60, helper -> {
            formSpreader(helper);
            helper.assertTrue(MultiblockCoreBlock.isFormed(helper.getBlockState(CORE)),
                "the four-cell tower must form");
            helper.assertBlockPresent(RCBlocks.WATER_TANK.get(), CORE.above());
            helper.assertBlockPresent(RCBlocks.GRASS_SPREADER_FRAME.get(), CORE.above(2));

            helper.getLevel().destroyBlock(helper.absolutePos(CORE), true);
            helper.succeedWhen(() -> {
                helper.assertItemEntityCountIs(RCItems.GRASS_SPREADER.get(), CORE, 4.0, 1);
                helper.assertItemEntityCountIs(RCItems.RAIN_COLLECTOR.get(), CORE, 4.0, 1);
                helper.assertItemEntityCountIs(RCItems.PUMP.get(), CORE, 4.0, 1);
                helper.assertItemEntityCountIs(RCItems.SOLAR_PANEL.get(), CORE, 4.0, 1);
            });
        });

        // The tank is an inert component, NOT a Rain Collector core. Nesting cores would mean
        // the inner one watching its own neighbours and trying to assemble itself into cells this
        // machine has already claimed - two machines fighting over the same blocks. Multiblock's
        // constructor now rejects that outright; this asserts the spreader takes the inert block.
        // (That the tank is not a core is proven by the compiler - WaterTankBlock and
        // MultiblockCoreBlock are unrelated types - and enforced at runtime by Multiblock's
        // constructor, so there is nothing left for an assertion to catch. What is worth asserting
        // is that the machine really assembles around the inert component.)
        RCGameTests.test("grass_spreader_uses_an_inert_tank_component", 20, helper -> {
            formSpreader(helper);
            helper.assertTrue(MultiblockCoreBlock.isFormed(helper.getBlockState(CORE)),
                "the tower must form with an inert Water Tank in the collector's place");
            helper.assertBlockPresent(RCBlocks.WATER_TANK.get(), CORE.above());
            helper.succeed();
        });
    }
}
