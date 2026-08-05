package com.flatts.recompile.gametest;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.MoundGroundBlock;
import com.flatts.recompile.content.block.MoundGroundBlock.Outcome;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;

/**
 * GameTests for mound regrowth (design P1.6, Phase 5): a mound is a renewable quarry that grows back
 * toward the size it was and never past it.
 *
 * <p>Driven through {@link MoundGroundBlock#regrowOnce}, the static entry point, rather than by
 * waiting on random ticks - the same convention as {@code SortableBlock.sortOnce}. The rate is a
 * config lever and is not what these assert; what it does when it fires is.
 */
final class MoundRegrowthTests {

    private MoundRegrowthTests() {
    }

    private static final BlockPos GROUND = new BlockPos(1, 1, 1);

    /**
     * Run a body with a short delivery drop.
     *
     * <p>The shipped default spawns the block 30 up, which is the right call in a world and the wrong
     * one in a 5x5x5 test plot: the flight-path check then reaches far outside the structure into
     * whatever the test world happens to have there, and every grow reports BLOCKED for a reason that
     * has nothing to do with the rule under test. Two is enough to prove the same code path.
     */
    private static void withShortDrop(Runnable body) {
        int was = RCConfig.MOUND_REGROWTH_DROP_HEIGHT.get();
        try {
            RCConfig.MOUND_REGROWTH_DROP_HEIGHT.set(2);
            body.run();
        } finally {
            RCConfig.MOUND_REGROWTH_DROP_HEIGHT.set(was);
        }
    }

    static void register() {
        // The core loop: a column that is short of what it remembers puts one block back, and stops
        // dead once it is whole. "Never beyond the original" is the half that makes a mound a quarry
        // instead of an expanding hazard, so it is asserted rather than assumed.
        RCGameTests.test("a_short_mound_grows_back_and_then_stops", 40, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(GROUND);
            helper.setBlock(GROUND, RCBlocks.MOUND_GROUND.get().defaultBlockState()
                .setValue(MoundGroundBlock.HEIGHT, 2));

            withShortDrop(() -> {
                Outcome first = MoundGroundBlock.regrowOnce(level, abs);
                helper.assertTrue(first == Outcome.GREW,
                    "an empty column that remembers 2 must grow, got " + first);
            });
            helper.assertEntityPresent(EntityType.FALLING_BLOCK);

            // Stand the remembered stack up by hand and it must consider itself finished. Asserted on
            // the full stack rather than on one block, because an off-by-one here builds every mound
            // in the world one block short and nothing else would ever say so.
            helper.setBlock(GROUND.above(1), RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(GROUND.above(2), RCBlocks.GARBAGE_BLOCK.get());
            helper.assertTrue(MoundGroundBlock.regrowOnce(level, abs) == Outcome.FULL,
                "a column of 2 that remembers 2 is finished - if this grows, mounds creep upward "
                    + "forever and the quarry becomes a hazard");
            helper.succeed();
        });

        // A block the player puts down is not the seed of a new mound. HEIGHT 0 is the default state,
        // so this is the case a creative player or a stray /setblock produces, and it must do nothing.
        RCGameTests.test("hand_placed_mound_ground_grows_nothing", 20, helper -> {
            ServerLevel level = helper.getLevel();
            helper.setBlock(GROUND, RCBlocks.MOUND_GROUND.get());
            helper.assertTrue(
                MoundGroundBlock.regrowOnce(level, helper.absolutePos(GROUND)) == Outcome.INERT,
                "mound ground with no remembered height must be inert - placing one down cannot "
                    + "start a mound that was never there");
            helper.succeed();
        });

        // Builds stop regrowth. The design says grass and placed blocks halt it; the subtler half is
        // the flight path, because the block is delivered from above and would otherwise land on a
        // player's roof and rebuild the mound on top of their structure.
        RCGameTests.test("a_build_over_a_mound_stops_it_regrowing", 20, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(GROUND);
            helper.setBlock(GROUND, RCBlocks.MOUND_GROUND.get().defaultBlockState()
                .setValue(MoundGroundBlock.HEIGHT, 3));

            helper.setBlock(GROUND.above(1), Blocks.STONE);
            helper.assertTrue(MoundGroundBlock.regrowOnce(level, abs) == Outcome.BLOCKED,
                "something standing in the column must stop regrowth, not be buried by it");

            // Clear the target but roof the flight path instead.
            helper.setBlock(GROUND.above(1), Blocks.AIR);
            helper.setBlock(GROUND.above(3), Blocks.STONE);
            helper.assertTrue(MoundGroundBlock.regrowOnce(level, abs) == Outcome.BLOCKED,
                "a roof over the mound must stop regrowth - without the flight-path check the block "
                    + "lands on the roof and the mound rebuilds itself on the player's build");
            helper.succeed();
        });

        // Retirement. Rung 1 greens the ground, the memory goes with the block, and that mound is
        // over. This is the whole quarry-or-heal decision, so it gets a test rather than a comment.
        RCGameTests.test("greening_mound_ground_retires_the_mound", 20, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(GROUND);
            helper.setBlock(GROUND, RCBlocks.MOUND_GROUND.get().defaultBlockState()
                .setValue(MoundGroundBlock.HEIGHT, 4));
            withShortDrop(() -> helper.assertTrue(
                MoundGroundBlock.regrowOnce(level, abs) == Outcome.GREW,
                "precondition: this mound is still alive"));

            helper.setBlock(GROUND, Blocks.GRASS_BLOCK);
            helper.assertTrue(MoundGroundBlock.regrowOnce(level, abs) == Outcome.INERT,
                "grass over the footprint must retire the mound permanently - the memory lives in "
                    + "the block, so replacing it is what forgets");
            helper.succeed();
        });

        // THE WIRING, which every other test here is blind to. They all call regrowOnce directly, so
        // the feature could be completely dead in a real world - the Properties flag missing, or the
        // override misspelled so it silently shadows nothing - and all of them would still pass. Two
        // assertions, because either alone leaves half the path unproven: the block must be REGISTERED
        // for random ticks, and its override must be the one the game actually calls.
        RCGameTests.test("the_random_tick_really_reaches_regrowth", 20, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(GROUND);
            helper.setBlock(GROUND, RCBlocks.MOUND_GROUND.get().defaultBlockState()
                .setValue(MoundGroundBlock.HEIGHT, 1));

            helper.assertTrue(level.getBlockState(abs).isRandomlyTicking(),
                "Mound Ground must be registered for random ticks - without randomTicks() in its "
                    + "Properties nothing ever calls it and mounds never grow back");

            int wasRarity = RCConfig.MOUND_REGROWTH_RARITY.get();
            int wasDrop = RCConfig.MOUND_REGROWTH_DROP_HEIGHT.get();
            try {
                RCConfig.MOUND_REGROWTH_RARITY.set(1);      // fire every tick, so this is not a dice roll
                RCConfig.MOUND_REGROWTH_DROP_HEIGHT.set(2);  // see withShortDrop
                level.getBlockState(abs).randomTick(level, abs, level.getRandom());
            } finally {
                RCConfig.MOUND_REGROWTH_RARITY.set(wasRarity);
                RCConfig.MOUND_REGROWTH_DROP_HEIGHT.set(wasDrop);
            }
            helper.assertEntityPresent(EntityType.FALLING_BLOCK);
            helper.succeed();
        });

        // Gravity off changes the DELIVERY, not the feature. GARBAGE_GRAVITY_ENABLED has promised
        // "deorbit on regrowth" in its own comment since Phase 0, so a pack that turns gravity off and
        // still gets blocks raining out of the sky has been lied to by the config file. Regrowth keeps
        // its own switch, so this must still fill the mound - just by placing rather than dropping.
        RCGameTests.test("gravity_off_places_the_block_instead_of_dropping_it", 20, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(GROUND);
            helper.setBlock(GROUND, RCBlocks.MOUND_GROUND.get().defaultBlockState()
                .setValue(MoundGroundBlock.HEIGHT, 2));

            boolean was = RCConfig.GARBAGE_GRAVITY_ENABLED.get();
            try {
                RCConfig.GARBAGE_GRAVITY_ENABLED.set(false);
                helper.assertTrue(MoundGroundBlock.regrowOnce(level, abs) == Outcome.GREW,
                    "regrowth must still work with gravity off - that flag governs the fall, not "
                        + "whether mounds come back");
            } finally {
                RCConfig.GARBAGE_GRAVITY_ENABLED.set(was);
            }
            helper.assertBlockPresent(RCBlocks.GARBAGE_BLOCK.get(), GROUND.above(1));
            helper.assertEntityNotPresent(EntityType.FALLING_BLOCK);
            helper.succeed();
        });

        // The config lever really is a lever. A gate that cannot be closed is a gate nobody can trust
        // in a pack, and "defaults are the design" only holds if the switch works.
        RCGameTests.test("regrowth_can_be_switched_off", 20, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(GROUND);
            helper.setBlock(GROUND, RCBlocks.MOUND_GROUND.get().defaultBlockState()
                .setValue(MoundGroundBlock.HEIGHT, 3));

            boolean was = RCConfig.MOUND_REGROWTH_ENABLED.get();
            try {
                RCConfig.MOUND_REGROWTH_ENABLED.set(false);
                helper.assertTrue(MoundGroundBlock.regrowOnce(level, abs) == Outcome.DISABLED,
                    "with regrowth off a mound must stay quarried");
            } finally {
                // Restore in finally: this config is global, so leaking it off would silently disable
                // regrowth for every test that runs after this one.
                RCConfig.MOUND_REGROWTH_ENABLED.set(was);
            }
            helper.succeed();
        });
    }
}
