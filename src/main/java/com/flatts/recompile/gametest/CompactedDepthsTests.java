package com.flatts.recompile.gametest;

import java.util.Set;
import java.util.HashSet;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.flatts.recompile.Recompile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The compacted depths: solid, bedrock roof to bedrock floor, and nothing else.
 *
 * <p><b>Measured against the shipped density function rather than by generating a world.</b> Same
 * instrument {@code TerrainSlabTests} uses on the overworld: {@link RandomState} hands back the fully
 * mapped router the game itself generates with, and {@code finalDensity} is sampled directly - positive
 * is solid. A regex over the JSON would pass the moment somebody edited the number it asserts.
 *
 * <p><b>This is the half a datapack parse check cannot reach.</b> The first version of these settings
 * loaded cleanly, generated cleanly, and filled the entire dimension with BEDROCK floor to ceiling -
 * because the roof's {@code vertical_gradient} was missing vanilla's {@code minecraft:not} wrapper and
 * so matched every block below top-5. Nothing errored. The registry was happy. It took walking in to
 * see it, and the density function was innocent throughout, which is why this test asserts the fill and
 * a separate one asserts the shell.
 */
final class CompactedDepthsTests {

    private static final ResourceKey<NoiseGeneratorSettings> DEPTHS = ResourceKey.create(
        Registries.NOISE_SETTINGS,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "compacted_depths"));

    /**
     * Two, where the overworld's slab test needs four.
     *
     * <p>This router is all constants - there is no continentalness to sample and no surface band to
     * catch - so one seed would genuinely prove as much as a hundred. The second is a cheap guard
     * against exactly that assumption stopping being true: the day somebody puts a noise term in
     * {@code final_density}, a single-seed test would keep passing on the one slice it happens to see.
     */
    private static final long[] SEEDS = {0L, 4242L};

    private CompactedDepthsTests() {
    }

    static void register() {

        // SOLID, EVERY COLUMN, ALL THE WAY UP. "You tunnel through it" is the whole design (P3.5 item
        // 3: "It is solid, not mounds"), and a density that dips negative anywhere would open caverns
        // the spec explicitly does not want - "voids: none, except embedded structures".
        RCGameTests.test("the_compacted_depths_are_solid_roof_to_floor", 20, helper -> {
            NoiseGeneratorSettings settings = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(DEPTHS).value();
            int minY = settings.noiseSettings().minY();
            int maxY = minY + settings.noiseSettings().height();
            helper.assertTrue(maxY - minY == 128,
                "the depths should span vanilla's nether height of 128, got " + (maxY - minY));

            List<String> hollow = new ArrayList<>();
            int sampled = 0;
            for (long seed : SEEDS) {
                DensityFunction density = RandomState
                    .create(helper.getLevel().registryAccess(), DEPTHS, seed)
                    .router().finalDensity();
                for (int x = -2000; x <= 2000; x += 1000) {
                    for (int z = -2000; z <= 2000; z += 1000) {
                        for (int y = minY; y < maxY; y += 4) {
                            sampled++;
                            double value = density.compute(
                                new DensityFunction.SinglePointContext(x, y, z));
                            if (value <= 0.0) {
                                hollow.add("seed " + seed + " at " + x + "," + y + "," + z
                                    + " density " + value);
                            }
                        }
                    }
                }
            }
            helper.assertTrue(sampled > 500,
                "only " + sampled + " points were sampled - discovery is broken, so this would pass "
                    + "against a hollow dimension");
            helper.assertTrue(hollow.isEmpty(),
                "the depths are meant to be solid and these points are air, which would open caverns "
                    + "the spec does not want (showing up to 5 of " + hollow.size() + "): "
                    + hollow.subList(0, Math.min(5, hollow.size())));
            helper.succeed();
        });

        // THE FILL IS THE MOD'S OWN BLOCK, not netherrack. That is what makes the dimension a dump you
        // mine rather than a vanilla Nether wearing a costume, and it is one line in the settings that
        // nothing else would notice changing.
        RCGameTests.test("the_depths_are_made_of_techno_organic_waste", 20, helper -> {
            NoiseGeneratorSettings settings = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(DEPTHS).value();
            helper.assertTrue(
                settings.defaultBlock().is(
                    com.flatts.recompile.registry.RCBlocks.TECHNO_ORGANIC_WASTE.get()),
                "the depths' default block is " + settings.defaultBlock()
                    + " - it must be techno-organic waste, or the dimension is netherrack again");
            helper.succeed();
        });

        // VANILLA STRUCTURES NEED THIS BIOME IN THEIR OWN biomes() SET, and that is the entire wiring:
        // both are biome-driven, so a themed biome hosts them with a two-line tag file and hosts
        // NOTHING without it. A fortress that never generates looks identical to one that generates far
        // away, so this cannot be left to a look in the world.
        //
        // ASKED OF THE STRUCTURE, NOT OF A TAG NAME. The first version built TagKeys from string
        // literals and checked the biome was in them - but those tags are created by this same
        // datapack, so a wrong path (`has_structure/fortress`, a typo, a rename in a later MC version)
        // would define a differently-named tag containing the biome and the test would pass green while
        // nothing generated. Reading Structure.biomes() measures the wiring the game actually consults.
        RCGameTests.test("the_depths_host_fortresses_and_bastions", 20, helper -> {
            var access = helper.getLevel().registryAccess();
            var holder = access.lookupOrThrow(Registries.BIOME).getOrThrow(
                ResourceKey.create(Registries.BIOME,
                    Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "compacted_depths")));
            var structures = access.lookupOrThrow(Registries.STRUCTURE);

            List<String> missing = new ArrayList<>();
            for (String id : List.of("fortress", "bastion_remnant")) {
                var key = ResourceKey.create(Registries.STRUCTURE,
                    Identifier.withDefaultNamespace(id));
                var structure = structures.getOptional(key).orElse(null);
                if (structure == null) {
                    missing.add("minecraft:" + id + " (no such structure)");
                } else if (!structure.biomes().contains(holder)) {
                    missing.add("minecraft:" + id);
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "these structures do not list the compacted depths among their biomes, so they will "
                    + "never generate there and nothing will say so: " + missing);
            helper.succeed();
        });

        // THE NETHER FLORA IS BOOTSTRAPPED, AND THIS IS WHAT MAKES THAT A FACT RATHER THAN A CLAIM.
        // Nothing generates nylium here - the depths are solid fill - so both nyliums are crafted from
        // shards, and everything else in the crimson and warped families hangs off bone meal applied to
        // them. That chain was asserted in a comment, in a loot table's prose, and in the resource
        // checklist's reason strings, and measured by none of them. It cost a wrong issue (#329, filed
        // saying 13 resources were unreachable, two weeks after the recipes shipped) and a wrong reason
        // string that is still generated (#360).
        //
        // Driven through performBonemeal directly rather than through a player holding bone meal: the
        // question is whether the BLOCK grows, and a player click also tests aim. Verified by hand in a
        // running client first, which is where the first stage was confirmed and the second stage's
        // click kept landing on the ground instead of the fungus.
        RCGameTests.test("bone_meal_on_crafted_nylium_grows_the_nether_flora", 300, helper -> {
            ServerLevel level = helper.getLevel();
            // WHAT IT ACTUALLY YIELDS, measured, because the resource checklist is about to declare
            // these as reachability edges and a hand-declared edge is a claim nothing re-checks. That
            // is how #329 came to assert 13 unreachable resources two weeks after the recipes shipped.
            Set<Block> crimson = sprout(helper, level, Blocks.CRIMSON_NYLIUM);
            Set<Block> warped = sprout(helper, level, Blocks.WARPED_NYLIUM);

            helper.assertTrue(crimson.contains(Blocks.CRIMSON_ROOTS),
                "bone meal on crimson nylium grew no crimson roots");
            helper.assertTrue(crimson.contains(Blocks.CRIMSON_FUNGUS),
                "bone meal on crimson nylium grew no crimson fungus, so the crimson wood family has "
                    + "nothing to start from");
            helper.assertTrue(warped.contains(Blocks.WARPED_ROOTS),
                "bone meal on warped nylium grew no warped roots");
            helper.assertTrue(warped.contains(Blocks.WARPED_FUNGUS),
                "bone meal on warped nylium grew no warped fungus, so the warped wood family has "
                    + "nothing to start from");
            helper.assertTrue(warped.contains(Blocks.NETHER_SPROUTS),
                "bone meal on warped nylium grew no nether sprouts, which have no other source here");
            helper.succeed();
        });

        // AND THE SECOND STAGE, which is the one that carries the wood. A fungus standing on its own
        // nylium grows a HUGE fungus, and that is where crimson_stem, shroomlight and the wart block
        // come from. Without it the first stage only yields decoration and #329's list stands.
        RCGameTests.test("a_fungus_on_its_nylium_grows_a_huge_fungus", 400, helper -> {
            ServerLevel level = helper.getLevel();
            // BOTH COLOURS, and the whole product set of each, because that set is exactly what the
            // resource checklist's reachability edges claim. Asserting only the stem would leave
            // shroomlight and the two wart blocks resting on the same untested assumption that
            // produced #329's wrong list.
            grow(helper, level, new BlockPos(2, 1, 2), Blocks.CRIMSON_NYLIUM, Blocks.CRIMSON_FUNGUS,
                Blocks.CRIMSON_STEM, Blocks.NETHER_WART_BLOCK);
            grow(helper, level, new BlockPos(2, 1, 2), Blocks.WARPED_NYLIUM, Blocks.WARPED_FUNGUS,
                Blocks.WARPED_STEM, Blocks.WARPED_WART_BLOCK);
            helper.succeed();
        });
    }

    /**
     * Plant {@code fungus} on its own nylium and bone-meal it until a huge fungus stands, then assert
     * its stem, its hat and a shroomlight are all present.
     *
     * <p>Retried rather than called once because {@code FungusBlock.isBonemealSuccess} is a dice roll,
     * and cleared overhead first because a huge fungus refuses without headroom - a failure that would
     * otherwise read as "the mechanic does not work in this world", which is the exact wrong conclusion
     * these two tests exist to prevent.
     */
    private static void grow(GameTestHelper helper, ServerLevel level, BlockPos soil,
            Block nyliumBlock, Block fungusBlock, Block stem, Block hat) {
        boolean sawStem = false;
        boolean sawHat = false;
        boolean sawShroomlight = false;

        // GROW SEVERAL, not one. The stem and the hat come with every huge fungus; SHROOMLIGHT IS A
        // PROBABILISTIC DECORATOR and a single fungus routinely has none - asserting it on one failed
        // here first. A player grows a grove, so the test does too, and the claim being checked is
        // "this is reachable from a nylium and bone meal", not "every fungus carries one".
        for (int attempt = 0; attempt < 24 && !(sawStem && sawHat && sawShroomlight); attempt++) {
            for (int y = 2; y <= 14; y++) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        helper.setBlock(new BlockPos(soil.getX() + dx, y, soil.getZ() + dz),
                            Blocks.AIR);
                    }
                }
            }
            helper.setBlock(soil, nyliumBlock);
            BlockPos cap = soil.above();
            BlockPos absCap = helper.absolutePos(cap);

            boolean grew = false;
            for (int i = 0; i < 40 && !grew; i++) {
                helper.setBlock(cap, fungusBlock);
                ((BonemealableBlock) fungusBlock).performBonemeal(
                    level, level.getRandom(), absCap, level.getBlockState(absCap));
                for (int dy = 1; dy <= 8 && !grew; dy++) {
                    grew = level.getBlockState(helper.absolutePos(soil.above(dy))).is(stem);
                }
            }
            if (!grew) {
                continue;
            }
            for (int dy = 1; dy <= 12; dy++) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockState at = level.getBlockState(
                            helper.absolutePos(soil.offset(dx, dy, dz)));
                        sawStem |= at.is(stem);
                        sawHat |= at.is(hat);
                        sawShroomlight |= at.is(Blocks.SHROOMLIGHT);
                    }
                }
            }
        }

        helper.assertTrue(sawStem, "bone meal on a fungus standing on its own nylium grew no "
            + stem.getName().getString() + ", so that whole wood family has no source in this world");
        helper.assertTrue(sawHat, "no huge fungus grew a " + hat.getName().getString());
        helper.assertTrue(sawShroomlight,
            "twenty-four huge fungi grew no shroomlight, which has no other source here");
    }


    /** Bone-meal a nylium many times over and return every distinct block that came up on it. */
    private static Set<Block> sprout(GameTestHelper helper, ServerLevel level, Block nyliumBlock) {
        Set<Block> seen = new HashSet<>();
        BlockPos soil = new BlockPos(2, 1, 2);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                helper.setBlock(new BlockPos(soil.getX() + dx, 1, soil.getZ() + dz), nyliumBlock);
                helper.setBlock(new BlockPos(soil.getX() + dx, 2, soil.getZ() + dz), Blocks.AIR);
            }
        }
        BlockPos abs = helper.absolutePos(soil);
        BonemealableBlock nylium = (BonemealableBlock) nyliumBlock;
        // Many passes: the spread is random and scatters over a radius, so one call proves nothing
        // about what the block CAN produce.
        for (int i = 0; i < 60; i++) {
            nylium.performBonemeal(level, level.getRandom(), abs, level.getBlockState(abs));
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockState at = level.getBlockState(helper.absolutePos(soil.offset(dx, 1, dz)));
                    if (!at.isAir()) {
                        seen.add(at.getBlock());
                    }
                    helper.setBlock(new BlockPos(soil.getX() + dx, 2, soil.getZ() + dz), Blocks.AIR);
                }
            }
        }
        return seen;
    }

}
