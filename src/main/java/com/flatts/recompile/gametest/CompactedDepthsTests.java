package com.flatts.recompile.gametest;

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
    }
}
