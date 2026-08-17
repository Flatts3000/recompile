package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * How much rock the world actually has under it, measured against the shipped density function.
 *
 * <p><b>Why this exists.</b> The garbage world was a coarse-dirt slab about 13 blocks thick floating on
 * roughly 120 blocks of void, because {@code final_density} clamped to air below y=55. Nothing on the
 * surface cared, so nothing noticed - until the sewers (#90), which need somewhere to be. A structure
 * has no error state for "there was no room": it simply generates nothing, or punches through the floor
 * into the void, and both look like the structure code being broken.
 *
 * <p><b>The depth is derived from vanilla's own mineshaft, which the sewer mirrors.</b>
 * {@code MineshaftPieces} caps recursion at {@code MAX_DEPTH = 8} and a stairs piece drops <b>5</b>
 * blocks per level ({@code findStairs} builds a box spanning y -5 to +2), so a worst-case chain descends
 * about 40 blocks. Add the root room, which is 5 to 10 tall, and a sewer wants roughly 45-50 blocks of
 * rock plus cover above it and clearance over bedrock. That is where {@link #MIN_ROCK} comes from - it
 * is not a number somebody liked the look of.
 *
 * <p><b>It measures the real function rather than reading the file.</b> A regex over the JSON would pass
 * the moment someone edits the gradient this asserts and would say nothing about the four other
 * gradients that also shape the column. {@link RandomState} gives the fully mapped router the game
 * itself generates with, and {@code finalDensity} is sampled directly: positive is solid.
 */
final class TerrainSlabTests {

    /** Deepest a worst-case mineshaft-shaped sprawl descends, plus its room and a little clearance. */
    private static final int MIN_ROCK = 45;

    /**
     * What the column owes before any of it is tunnelable: three blocks of coarse dirt at the top
     * ({@code stone_depth} floor, offset 2) and one of bedrock underneath ({@code stone_depth} ceiling,
     * offset 0). Measuring the whole solid run against {@link #MIN_ROCK} silently permitted four fewer
     * blocks of rock than the number claims to protect.
     */
    private static final int NON_ROCK = 4;

    /**
     * More than one seed, because one seed exercises exactly one continentalness field. That is harmless
     * for the floor, which is a pure gradient, and not harmless for the roof - the surface band is the
     * half of this test that guards existing saves, and a single seed can only ever see one slice of it.
     */
    private static final long[] SEEDS = {0L, 1234L, -99L, 8675309L};

    /**
     * The surface must not move. Growing the slab downward is invisible from above by construction - the
     * floor gradient and the surface gradients are separate terms - and this is what keeps it that way,
     * because a change that quietly raised the ground would break every mound, spreader and farm plot in
     * every existing save.
     *
     * <p><b>These are the structural bounds, not a comfortable margin around them.</b> The surface term
     * is {@code y_clamped_gradient(63 -> 69, 1 -> -1)} plus continentalness scaled by 0.9, so it crosses
     * zero between y=63.3 with the noise at its most negative and y=68.7, and the {@code 68 -> 71}
     * gradient clamps anything above 69.5. Measured across four seeds and 324 columns the roof lands in
     * exactly 63..69 - the arithmetic and the measurement agreeing. A looser band would let the whole
     * surface shift by a block or three, which is precisely the save-breaking change this exists to
     * catch, and pass green.
     */
    private static final int SURFACE_MIN = 63;
    private static final int SURFACE_MAX = 69;

    private TerrainSlabTests() {
    }

    static void register() {
        RCGameTests.test("the_world_has_rock_enough_to_hold_a_sewer", 20, helper -> {
            ResourceKey<NoiseGeneratorSettings> key = ResourceKey.create(Registries.NOISE_SETTINGS,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "garbage"));
            NoiseGeneratorSettings settings = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key).value();
            int minY = settings.noiseSettings().minY();
            int maxY = minY + settings.noiseSettings().height();

            List<String> thin = new ArrayList<>();
            List<String> moved = new ArrayList<>();
            for (long seed : SEEDS) {
                DensityFunction density = RandomState
                    .create(helper.getLevel().registryAccess(), key, seed)
                    .router().finalDensity();
                // Sampled across a wide span rather than at one column: the surface carries a
                // continentalness term, so a single column proves nothing about the world.
                for (int x = -2000; x <= 2000; x += 500) {
                    for (int z = -2000; z <= 2000; z += 500) {
                        // LONGEST CONTIGUOUS RUN, not first-solid to last-solid. The two agree only
                        // while the column has no gap in it, and the whole point of this test is that a
                        // later terrain edit cannot quietly take the room back - a change that split the
                        // slab into two thin layers would keep the endpoints far apart and pass green
                        // with no sewer able to fit anywhere.
                        int longest = 0;
                        int run = 0;
                        int roof = Integer.MIN_VALUE;
                        for (int y = minY; y < maxY; y++) {
                            if (density.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0.0) {
                                run++;
                                longest = Math.max(longest, run);
                                roof = y;
                            } else {
                                run = 0;
                            }
                        }
                        String where = "seed " + seed + " (" + x + "," + z + ")";
                        if (longest == 0) {
                            thin.add(where + " has no solid ground at all");
                            continue;
                        }
                        if (longest - NON_ROCK < MIN_ROCK) {
                            thin.add(where + " has only " + (longest - NON_ROCK) + " blocks of rock");
                        }
                        if (roof < SURFACE_MIN || roof > SURFACE_MAX) {
                            moved.add(where + " tops out at y=" + roof);
                        }
                    }
                }
            }

            helper.assertTrue(thin.isEmpty(),
                "the world is too thin to hold a sewer - a structure given no room does not fail, it "
                    + "silently generates nothing or drops into the void, and both read as broken "
                    + "structure code: " + thin);
            helper.assertTrue(moved.isEmpty(),
                "the surface moved, which growing the slab downward must never do - every mound, "
                    + "spreader and farm plot in every existing save sits on it: " + moved);
            helper.succeed();
        });
    }
}
