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
     * The surface must not move. Growing the slab downward is invisible from above by construction - the
     * floor gradient and the surface gradients are separate terms - and this is what keeps it that way,
     * because a change that quietly raised the ground would break every mound, spreader and farm plot in
     * every existing save.
     */
    private static final int SURFACE_MIN = 60;
    private static final int SURFACE_MAX = 71;

    private TerrainSlabTests() {
    }

    static void register() {
        RCGameTests.test("the_world_has_rock_enough_to_hold_a_sewer", 20, helper -> {
            ResourceKey<NoiseGeneratorSettings> key = ResourceKey.create(Registries.NOISE_SETTINGS,
                Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "garbage"));
            NoiseGeneratorSettings settings = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key).value();
            RandomState state = RandomState.create(
                helper.getLevel().registryAccess(), key, 0L);
            DensityFunction density = state.router().finalDensity();

            int minY = settings.noiseSettings().minY();
            int maxY = minY + settings.noiseSettings().height();

            List<String> thin = new ArrayList<>();
            List<String> moved = new ArrayList<>();
            // Sampled across a wide span rather than at one column: the surface carries a
            // continentalness term, so a single column proves nothing about the world.
            for (int x = -2000; x <= 2000; x += 500) {
                for (int z = -2000; z <= 2000; z += 500) {
                    int floor = Integer.MIN_VALUE;
                    int roof = Integer.MIN_VALUE;
                    for (int y = minY; y < maxY; y++) {
                        boolean solid = density.compute(
                            new DensityFunction.SinglePointContext(x, y, z)) > 0.0;
                        if (solid) {
                            if (floor == Integer.MIN_VALUE) {
                                floor = y;
                            }
                            roof = y;
                        }
                    }
                    if (floor == Integer.MIN_VALUE) {
                        thin.add("(" + x + "," + z + ") has no solid ground at all");
                        continue;
                    }
                    if (roof - floor < MIN_ROCK) {
                        thin.add("(" + x + "," + z + ") is only " + (roof - floor + 1) + " thick");
                    }
                    if (roof < SURFACE_MIN || roof > SURFACE_MAX) {
                        moved.add("(" + x + "," + z + ") tops out at y=" + roof);
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
