package com.flatts.recompile.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The water economy: two buckets must not breed a third source (#101).
 *
 * <p>Asserting the game rule's value would be nearly worthless - it would prove a field was written,
 * not that water behaves. So these place the actual diagonal-source arrangement players use and check
 * what the world does with it, which is also the only version that would survive Mojang changing how
 * the rule is applied.
 */
final class WaterEconomyTests {

    private WaterEconomyTests() {
    }

    /**
     * Build the classic infinite-water shape: two sources with a gap between them, in a trough so the
     * flow cannot escape sideways. Vanilla turns the middle into a third source; we want it not to.
     */
    private static void buildTrough(GameTestHelper helper, BlockPos left) {
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.setBlock(left.offset(dx, -1, dz), Blocks.STONE);
                if (dz != 0) {
                    helper.setBlock(left.offset(dx, 0, dz), Blocks.STONE);
                }
            }
        }
        helper.setBlock(left.offset(-1, 0, 0), Blocks.STONE);
        helper.setBlock(left.offset(3, 0, 0), Blocks.STONE);
        helper.setBlock(left, Blocks.WATER);
        helper.setBlock(left.offset(1, 0, 0), Blocks.AIR);
        helper.setBlock(left.offset(2, 0, 0), Blocks.WATER);
    }

    static void register() {
        // ONE test, not two, and that is a correction. Written as a pair - "rule off, no source" and
        // "rule on, source" - they failed, because GameTests share a level and therefore share its game
        // rules, so the second was flipping the rule out from under the first. Same shape of bug as the
        // roaches eating the crumble test's pulls: a global that two tests both write.
        //
        // Sequential phases in one test also make the stronger claim: the SAME arrangement behaves
        // differently, and the rule is the only thing that changed.
        RCGameTests.test("the_water_rule_decides_whether_two_sources_breed_a_third", 120, helper -> {
            var rules = helper.getLevel().getGameRules();
            var server = helper.getLevel().getServer();
            boolean was = rules.get(GameRules.WATER_SOURCE_CONVERSION);

            // Phase 1: rule ON. This must make a source, or phase 2 proves nothing - a trough that never
            // fills would pass "no source" for entirely the wrong reason.
            rules.set(GameRules.WATER_SOURCE_CONVERSION, true, server);
            BlockPos on = new BlockPos(1, 2, 2);
            buildTrough(helper, on);

            helper.runAfterDelay(40, () -> {
                boolean bred = helper.getBlockState(on.offset(1, 0, 0)).getFluidState().isSource();
                helper.assertTrue(bred,
                    "with the rule ON the gap must become a source - if it does not, this arrangement "
                        + "never made infinite water and the rest of the test is meaningless");

                // Phase 2: rule OFF, fresh trough well away from the first so no leftover water helps.
                rules.set(GameRules.WATER_SOURCE_CONVERSION, false, server);
                BlockPos off = new BlockPos(8, 2, 2);
                buildTrough(helper, off);

                helper.runAfterDelay(40, () -> {
                    var state = helper.getBlockState(off.offset(1, 0, 0));
                    rules.set(GameRules.WATER_SOURCE_CONVERSION, was, server);   // restore before asserting
                    helper.assertTrue(state.is(Blocks.WATER),
                        "the gap should still fill with flowing water, just not a source - got " + state);
                    helper.assertFalse(state.getFluidState().isSource(),
                        "the gap became a SOURCE with the rule off, so infinite water still works and "
                            + "the Rain Collector is obsolete the moment a player has two buckets");
                    helper.succeed();
                });
            });
        });
    }
}
