package com.flatts.recompile.gametest;

import com.flatts.recompile.event.RCEncroachment;
import com.flatts.recompile.event.RCEncroachment.Outcome;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * GameTests for encroachment (design P1.7-R): the junkyard fights back. Each test drives
 * {@link RCEncroachment#encroachOnce} directly rather than waiting on the sampling sweep, so a
 * rung is asserted in one tick instead of being chased through random sampling.
 *
 * <p>The plot is bare, so every block not placed here is air - and air is not hostile ground,
 * which is why the interior case needs nothing but a plane of grass.
 */
final class EncroachmentTests {

    /** Centre of the shared 5x5x5 plot. Every case works outward from here. */
    private static final BlockPos SOIL = new BlockPos(2, 2, 2);
    /** The neighbour that puts {@link #SOIL} on the frontier. */
    private static final BlockPos NEIGHBOUR = new BlockPos(3, 2, 2);

    private EncroachmentTests() {
    }

    static void register() {
        // Rung 1. The base case the whole system rests on: bare healed soil touching bare
        // earth loses. If this stops firing, healing has silently become free again.
        RCGameTests.test("encroachment_takes_bare_frontier_grass", 10, helper -> {
            helper.setBlock(SOIL, Blocks.GRASS_BLOCK);
            helper.setBlock(NEIGHBOUR, Blocks.COARSE_DIRT);

            Outcome outcome = RCEncroachment.encroachOnce(helper.getLevel(), helper.absolutePos(SOIL));

            helper.assertTrue(outcome == Outcome.REVERTED,
                "bare grass on the frontier must revert, got " + outcome);
            helper.assertTrue(helper.getBlockState(SOIL).is(Blocks.COARSE_DIRT),
                "the reverted block must be coarse dirt");
            helper.succeed();
        });

        // Interior immunity - the half of "contested frontier only" that keeps this from being
        // rot. Grass with no unhealed neighbour is safe until the front actually reaches it.
        RCGameTests.test("encroachment_spares_interior_grass", 10, helper -> {
            for (int x = 1; x <= 3; x++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.GRASS_BLOCK);
                }
            }

            Outcome outcome = RCEncroachment.encroachOnce(helper.getLevel(), helper.absolutePos(SOIL));

            helper.assertTrue(outcome == Outcome.INTERIOR,
                "grass ringed by grass must be spared, got " + outcome);
            helper.assertTrue(helper.getBlockState(SOIL).is(Blocks.GRASS_BLOCK),
                "spared grass must still be grass");
            helper.succeed();
        });

        // Rung 2. Cover absorbs the hit rather than lowering a probability: the plant is torn
        // out and the soil survives, so a border visibly goes bare before it goes brown.
        RCGameTests.test("encroachment_strips_cover_and_spares_soil", 10, helper -> {
            helper.setBlock(SOIL, Blocks.GRASS_BLOCK);
            helper.setBlock(NEIGHBOUR, Blocks.COARSE_DIRT);
            helper.setBlock(SOIL.above(), Blocks.POPPY);

            Outcome outcome = RCEncroachment.encroachOnce(helper.getLevel(), helper.absolutePos(SOIL));

            helper.assertTrue(outcome == Outcome.COVER_STRIPPED,
                "cover must absorb the hit, got " + outcome);
            helper.assertTrue(helper.getBlockState(SOIL).is(Blocks.GRASS_BLOCK),
                "the soil under stripped cover must survive");
            helper.assertTrue(helper.getBlockState(SOIL.above()).isAir(),
                "the cover must be torn out");
            helper.succeed();
        });

        // Rung 3, and the reason wood is treasure: a log ends the fight for the ground around it.
        // Checked before cover, so an anchored block is never even stripped.
        RCGameTests.test("encroachment_yields_to_a_tree_anchor", 10, helper -> {
            helper.setBlock(SOIL, Blocks.GRASS_BLOCK);
            helper.setBlock(NEIGHBOUR, Blocks.COARSE_DIRT);
            helper.setBlock(new BlockPos(1, 2, 1), Blocks.OAK_LOG);

            Outcome outcome = RCEncroachment.encroachOnce(helper.getLevel(), helper.absolutePos(SOIL));

            helper.assertTrue(outcome == Outcome.ANCHORED,
                "a log in range must hold the frontier, got " + outcome);
            helper.assertTrue(helper.getBlockState(SOIL).is(Blocks.GRASS_BLOCK),
                "anchored grass must survive");
            helper.succeed();
        });

        // An unquarried mound presses on your grass exactly like bare earth does - otherwise
        // healing right up against a mound would be a safe way to dodge the fight entirely.
        RCGameTests.test("encroachment_counts_garbage_as_hostile_ground", 10, helper -> {
            helper.setBlock(SOIL, Blocks.GRASS_BLOCK);
            helper.setBlock(NEIGHBOUR, RCBlocks.GARBAGE_BLOCK.get());

            Outcome outcome = RCEncroachment.encroachOnce(helper.getLevel(), helper.absolutePos(SOIL));

            helper.assertTrue(outcome == Outcome.REVERTED,
                "a garbage block must count as hostile ground, got " + outcome);
            helper.succeed();
        });

        // Builds are never a target. P1.6 item 4 is explicit that this must not threaten what
        // the player put down, and the only thing enforcing that is grass being the sole target.
        RCGameTests.test("encroachment_never_touches_built_blocks", 10, helper -> {
            helper.setBlock(SOIL, RCBlocks.PRESSED_JUNK_BLOCK.get());
            helper.setBlock(NEIGHBOUR, Blocks.COARSE_DIRT);

            Outcome outcome = RCEncroachment.encroachOnce(helper.getLevel(), helper.absolutePos(SOIL));

            helper.assertTrue(outcome == Outcome.NOT_A_TARGET,
                "a built block must never be a target, got " + outcome);
            helper.assertTrue(helper.getBlockState(SOIL).is(RCBlocks.PRESSED_JUNK_BLOCK.get()),
                "a built block must be left alone");
            helper.succeed();
        });

        // The three tags are the whole tuning surface, and a typo in one of their JSON paths
        // fails silently - the sweep would just decide nothing is ever hostile and do nothing.
        RCGameTests.test("encroachment_tags_are_populated", 10, helper -> {
            helper.assertTrue(Blocks.COARSE_DIRT.defaultBlockState().is(RCTags.HOSTILE_GROUND),
                "coarse dirt must be hostile ground");
            helper.assertTrue(
                RCBlocks.GARBAGE_BLOCK.get().defaultBlockState().is(RCTags.HOSTILE_GROUND),
                "the garbage block must be hostile ground");
            helper.assertTrue(Blocks.OAK_LOG.defaultBlockState().is(RCTags.FRONTIER_ANCHOR),
                "logs must anchor the frontier");
            helper.assertTrue(Blocks.OAK_LEAVES.defaultBlockState().is(RCTags.FRONTIER_ANCHOR),
                "leaves must anchor the frontier");
            helper.assertTrue(Blocks.POPPY.defaultBlockState().is(RCTags.FRONTIER_COVER),
                "flowers must count as cover");
            helper.assertTrue(Blocks.SHORT_GRASS.defaultBlockState().is(RCTags.FRONTIER_COVER),
                "short grass must count as cover");
            helper.succeed();
        });
    }
}
