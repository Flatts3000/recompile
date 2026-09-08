package com.flatts.recompile.gametest;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.RegrowingGroundBlock;
import com.flatts.recompile.content.block.RegrowingGroundBlock.Outcome;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * GameTests for mound regrowth (design P1.6, Phase 5): a mound is a renewable quarry that grows back
 * toward the size it was and never past it.
 *
 * <p>Driven through {@link RegrowingGroundBlock#regrowOnce}, the static entry point, rather than by
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
                .setValue(RegrowingGroundBlock.HEIGHT, 2));

            withShortDrop(() -> {
                Outcome first = RegrowingGroundBlock.regrowOnce(level, abs);
                helper.assertTrue(first == Outcome.GREW,
                    "an empty column that remembers 2 must grow, got " + first);
            });
            helper.assertEntityPresent(EntityType.FALLING_BLOCK);

            // Stand the remembered stack up by hand and it must consider itself finished. Asserted on
            // the full stack rather than on one block, because an off-by-one here builds every mound
            // in the world one block short and nothing else would ever say so.
            helper.setBlock(GROUND.above(1), RCBlocks.GARBAGE_BLOCK.get());
            helper.setBlock(GROUND.above(2), RCBlocks.GARBAGE_BLOCK.get());
            helper.assertTrue(RegrowingGroundBlock.regrowOnce(level, abs) == Outcome.FULL,
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
                RegrowingGroundBlock.regrowOnce(level, helper.absolutePos(GROUND)) == Outcome.INERT,
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
                .setValue(RegrowingGroundBlock.HEIGHT, 3));

            helper.setBlock(GROUND.above(1), Blocks.STONE);
            helper.assertTrue(RegrowingGroundBlock.regrowOnce(level, abs) == Outcome.BLOCKED,
                "something standing in the column must stop regrowth, not be buried by it");

            // Clear the target but roof the flight path instead.
            helper.setBlock(GROUND.above(1), Blocks.AIR);
            helper.setBlock(GROUND.above(3), Blocks.STONE);
            helper.assertTrue(RegrowingGroundBlock.regrowOnce(level, abs) == Outcome.BLOCKED,
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
                .setValue(RegrowingGroundBlock.HEIGHT, 4));
            withShortDrop(() -> helper.assertTrue(
                RegrowingGroundBlock.regrowOnce(level, abs) == Outcome.GREW,
                "precondition: this mound is still alive"));

            helper.setBlock(GROUND, Blocks.GRASS_BLOCK);
            helper.assertTrue(RegrowingGroundBlock.regrowOnce(level, abs) == Outcome.INERT,
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
                .setValue(RegrowingGroundBlock.HEIGHT, 1));

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
                .setValue(RegrowingGroundBlock.HEIGHT, 2));

            boolean was = RCConfig.GARBAGE_GRAVITY_ENABLED.get();
            try {
                RCConfig.GARBAGE_GRAVITY_ENABLED.set(false);
                helper.assertTrue(RegrowingGroundBlock.regrowOnce(level, abs) == Outcome.GREW,
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
                .setValue(RegrowingGroundBlock.HEIGHT, 3));

            boolean was = RCConfig.MOUND_REGROWTH_ENABLED.get();
            try {
                RCConfig.MOUND_REGROWTH_ENABLED.set(false);
                helper.assertTrue(RegrowingGroundBlock.regrowOnce(level, abs) == Outcome.DISABLED,
                    "with regrowth off a mound must stay quarried");
            } finally {
                // Restore in finally: this config is global, so leaking it off would silently disable
                // regrowth for every test that runs after this one.
                RCConfig.MOUND_REGROWTH_ENABLED.set(was);
            }
            helper.succeed();
        });

        // EACH REGION PUTS BACK ITS OWN MATERIAL (P1.6-R). The whole of the multi-region change is
        // that regrowOnce reads the product off the ground block instead of naming the garbage block,
        // so this is the assertion that the change happened at all. Driven with gravity OFF so the
        // block lands in the world this tick and can be identified - with gravity on the answer is a
        // falling entity, and "a FallingBlockEntity exists" cannot tell garbage from tailings.
        RCGameTests.test("each_ground_grows_back_its_own_material", 40, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(GROUND);

            record Pair(Block ground, Block grown, String region) { }
            List<Pair> pairs = List.of(
                new Pair(RCBlocks.MOUND_GROUND.get(), RCBlocks.GARBAGE_BLOCK.get(), "household sprawl"),
                new Pair(RCBlocks.RUBBLE_GROUND.get(), RCBlocks.STONE_RUBBLE.get(), "demolition yard"),
                new Pair(RCBlocks.STAINED_GROUND.get(), RCBlocks.MILL_TAILINGS.get(), "radioactive dump"));

            boolean was = RCConfig.GARBAGE_GRAVITY_ENABLED.get();
            try {
                RCConfig.GARBAGE_GRAVITY_ENABLED.set(false);
                for (Pair pair : pairs) {
                    helper.setBlock(GROUND.above(1), Blocks.AIR);
                    helper.setBlock(GROUND, pair.ground().defaultBlockState()
                        .setValue(RegrowingGroundBlock.HEIGHT, 1));
                    Outcome outcome = RegrowingGroundBlock.regrowOnce(level, abs);
                    helper.assertTrue(outcome == Outcome.GREW,
                        pair.region() + " remembers a column of 1 and grew nothing, got " + outcome);
                    helper.assertTrue(
                        level.getBlockState(helper.absolutePos(GROUND.above(1))).is(pair.grown()),
                        pair.region() + " grew the wrong material - a ground block that puts back "
                            + "another region's pile is worse than one that puts back nothing, "
                            + "because it quietly converts one region into another");
                }
            } finally {
                RCConfig.GARBAGE_GRAVITY_ENABLED.set(was);
            }
            helper.succeed();
        });

        // THE FLAG THAT MAKES ANY OF THIS RUN IN A REAL GAME, AND ITS ABSENCE IS SILENT.
        //
        // <p>regrowOnce is driven directly by every test above, so the whole suite passes whether or
        // not a ground block actually random-ticks. In a world the random tick is the ONLY caller, so
        // a missing randomTicks() in Properties means that region simply never refills, with nothing
        // logged and nothing red. Stained Ground shipped without the flag for a fortnight - correctly,
        // because it was dressing then - and needed one the moment it became a memory.
        //
        // <p>Derived from the registry rather than listed, so a fourth region is covered the day it is
        // registered rather than the day somebody remembers this test exists.
        RCGameTests.test("every_regrowing_ground_random_ticks", 20, helper -> {
            List<String> deaf = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block instanceof RegrowingGroundBlock
                        && !block.defaultBlockState().isRandomlyTicking()) {
                    deaf.add(BuiltInRegistries.BLOCK.getKey(block).toString());
                }
            }
            helper.assertTrue(deaf.isEmpty(),
                "these remember a pile but never tick, so the pile never comes back and nothing "
                    + "anywhere says so - add randomTicks() to their Properties: " + deaf);
            helper.succeed();
        });

        // A FOREIGN PILE BLOCK IN THE COLUMN MUST NOT STALL IT, and the kind-aware version does.
        //
        // <p>Once three regions regrow, a column can contain a block some other feature put there. The
        // column walk is deliberately blind to WHICH pile block it finds: it counts the cell as filled
        // and grows above it. The tempting alternative - only count my own material - targets that
        // cell instead, finds it occupied by something not replaceable, and returns BLOCKED on every
        // tick for the rest of the world's life. This pins the forgiving behaviour so nobody
        // "corrects" it into the stalling one.
        RCGameTests.test("a_foreign_pile_block_does_not_stall_the_column", 40, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(GROUND);
            helper.setBlock(GROUND, RCBlocks.MOUND_GROUND.get().defaultBlockState()
                .setValue(RegrowingGroundBlock.HEIGHT, 2));
            // Another region's material, sitting in cell 1 of a garbage mound's column.
            helper.setBlock(GROUND.above(1), RCBlocks.MILL_TAILINGS.get());

            boolean was = RCConfig.GARBAGE_GRAVITY_ENABLED.get();
            try {
                RCConfig.GARBAGE_GRAVITY_ENABLED.set(false);
                Outcome outcome = RegrowingGroundBlock.regrowOnce(level, abs);
                helper.assertTrue(outcome == Outcome.GREW,
                    "a foreign pile block in the column stalled it with " + outcome + ". The walk "
                        + "must count it as filled and grow ABOVE it, or a column can never pass a "
                        + "block it did not place");
                helper.assertTrue(
                    level.getBlockState(helper.absolutePos(GROUND.above(2)))
                        .is(RCBlocks.GARBAGE_BLOCK.get()),
                    "it grew somewhere other than the first free cell above the foreign block");
            } finally {
                RCConfig.GARBAGE_GRAVITY_ENABLED.set(was);
            }
            helper.succeed();
        });

        // RETIREMENT IS A TAG, AND THE DUMP IS DELIBERATELY OUT OF IT (owner, 2026-09-08).
        //
        // <p>Greening a footprint is what retires a pile, so which grounds the Grass Spreader can
        // convert IS the retirement rule - there is no code anywhere saying "a tailings impoundment
        // never retires", it falls out of this tag. That makes the ruling a one-line data change away
        // from being reversed by accident, which is exactly why it is asserted.
        RCGameTests.test("the_yard_can_be_retired_and_the_dump_cannot", 20, helper -> {
            helper.assertTrue(RCBlocks.MOUND_GROUND.get().defaultBlockState().is(RCTags.SPREADABLE),
                "mound ground left #recompile:spreadable, so a garbage mound can no longer be retired "
                    + "and the sprawl became an unbounded quarry");
            helper.assertTrue(RCBlocks.RUBBLE_GROUND.get().defaultBlockState().is(RCTags.SPREADABLE),
                "rubble ground is not in #recompile:spreadable, so a rubble pile refills forever with "
                    + "no way to reclaim the yard - the demolition yard is NOT the region that was "
                    + "ruled non-reclaimable");
            helper.assertTrue(!RCBlocks.STAINED_GROUND.get().defaultBlockState().is(RCTags.SPREADABLE),
                "stained ground joined #recompile:spreadable, which reverses two rulings at once: "
                    + "contamination that scrubs clean is not contamination (2026-08-05), and the "
                    + "radioactive dump is permanently non-reclaimable (2026-09-08)");
            helper.succeed();
        });
    }
}
