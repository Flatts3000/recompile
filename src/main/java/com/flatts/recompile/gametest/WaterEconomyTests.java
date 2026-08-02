package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.event.RCWaterEconomy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
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
        // The GUARD, which the mechanic test above does not touch. It proves water behaves when the rule
        // is off; nothing proved the mod turns it off in the right worlds and leaves everything else
        // alone. A guard that always returned true would pass every other test in this file.
        //
        // Both directions, because a one-sided check here is worthless: "returns true for a garbage
        // world" is also true of a method that returns true for everything.
        RCGameTests.test("only_garbage_worlds_lose_their_infinite_water", 20, helper -> {
            // NEGATIVE: the GameTest server's own world is not a garbage world, so the mod must not
            // touch it. This is the case that protects a vanilla world with the mod installed.
            helper.assertFalse(
                RCWaterEconomy.isGarbageWorld(helper.getLevel().getChunkSource().getGenerator()),
                "the gametest world is not a garbage world, so the water rule must be left alone here");

            // POSITIVE: a generator built on the mod's own noise settings, the way the region tests
            // build a biome source. Keyed on noise settings rather than the biome source deliberately -
            // the biome source changed in 0.3.0, so a check against it would skip every older save.
            var settings = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(ResourceKey.create(Registries.NOISE_SETTINGS,
                    Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "garbage")));
            var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            var generator = new NoiseBasedChunkGenerator(
                new FixedBiomeSource(biomes.getOrThrow(ResourceKey.create(Registries.BIOME,
                    Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "household_sprawl")))),
                settings);
            helper.assertTrue(RCWaterEconomy.isGarbageWorld(generator),
                "a world generated from recompile:garbage noise settings must be recognised");
            helper.succeed();
        });

        // EVERY biome in this world must rain, and the Rain Collector is why.
        //
        // The collector fills on isRaining() + canSeeSky rather than isRainingAt(), deliberately: the
        // latter also demands the BIOME's precipitation at the spot, which over-couples the machine to
        // climate. The consequence is that a biome shipping has_precipitation:false does not stop the
        // collector - it fills perfectly well while the sky above it stays dry and silent. That is
        // exactly what the demolition yard did from #47 until 2026-08-02, and nothing caught it because
        // no test and no mechanic reads the flag. It was visible only by standing in the yard during a
        // storm and noticing the weather had stopped.
        //
        // Water is the resource the whole mod turns on, so "does it rain here" is not decoration.
        RCGameTests.test("every_biome_in_this_world_has_precipitation", 20, helper -> {
            var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            for (String path : new String[] {"household_sprawl", "demolition_yard"}) {
                var biome = biomes.getOrThrow(ResourceKey.create(Registries.BIOME,
                    Identifier.fromNamespaceAndPath(Recompile.MOD_ID, path)));
                helper.assertTrue(biome.value().hasPrecipitation(),
                    path + " must have precipitation - the Rain Collector fills on global weather, so a "
                        + "dry biome does not gate water, it just makes the water arrive from a clear sky");
            }
            helper.succeed();
        });
    }
}
