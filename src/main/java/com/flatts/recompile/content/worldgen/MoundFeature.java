package com.flatts.recompile.content.worldgen;

import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A pile of garbage blocks (design P0.2): the mounds that crowd the coarse-dirt
 * plain and make the world read as an endless dump. Each mound is a rounded dome
 * of random radius and height, so the field varies instead of tiling. Density is
 * controlled by the placed-feature count (config-tunable later).
 *
 * <p>The block mix (P1.1) follows the mound shape: trash bags scatter on the outer
 * surface (easy litter), compacted bales concentrate in the core (the mound shape
 * does the depth-reward work), and Bulky Waste is the uncommon pocket find inside.
 * Per-column heightmap sampling can refine the skirt later if needed.
 */
public class MoundFeature extends Feature<NoneFeatureConfiguration> {

    // Height and width are drawn independently and uniformly, so the field mixes
    // tall spires, low wide heaps, and everything between. Width is a diameter.
    private static final int MIN_HEIGHT = 3;
    private static final int MAX_HEIGHT = 15;
    private static final int MIN_WIDTH = 4;
    private static final int MAX_WIDTH = 15;

    private static final float SURFACE_BAG_CHANCE = 0.22F;
    private static final float CORE_BALE_CHANCE = 0.35F;
    /**
     * Bulky Waste per core cell (P1.11). Inherited unchanged from the appliance it
     * replaced, because it is already playtested: measured 2026-07-15 at ~2.41 per mound
     * and ~12 per chunk against ~48.5 core-eligible cells, so most mounds hold a couple
     * and tearing into one pays off. That is the *beat* - how often "something big is
     * buried here" fires. Which find it turns out to be is the loot table's job, not this
     * number's, so a new find never needs worldgen retuned.
     */
    private static final float CORE_BULKY_WASTE_CHANCE = 0.05F;

    public MoundFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        int width = MIN_WIDTH + random.nextInt(MAX_WIDTH - MIN_WIDTH + 1);
        double radius = width / 2.0;
        int r = (int) Math.floor(radius);

        boolean placedAny = false;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (dist > radius) {
                    continue;
                }
                // Dome profile: tallest at the center, tapering to a 1-block rim.
                int column = (int) Math.round(height * (1.0 - dist / radius));
                boolean core = dist < radius * 0.4;
                for (int dy = 0; dy <= column; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.getBlockState(pos).isAir()) {
                        boolean surface = dy == column;
                        level.setBlock(pos, pickBlock(random, core, dy, column, surface), 2);
                        placedAny = true;
                    }
                }
            }
        }
        return placedAny;
    }

    /** Pick the block for a mound cell: bags on the surface, bales/bulky waste in the core. */
    private BlockState pickBlock(RandomSource random, boolean core, int dy, int column, boolean surface) {
        if (surface && random.nextFloat() < SURFACE_BAG_CHANCE) {
            return RCBlocks.TRASH_BAG.get().defaultBlockState();
        }
        if (core && dy <= column * 0.5) {
            // One roll shared by both: bulky waste takes the bottom band, bales the next.
            // So the bale chance is offset by the bulky one - change either and both move.
            float roll = random.nextFloat();
            if (roll < CORE_BULKY_WASTE_CHANCE) {
                return RCBlocks.BULKY_WASTE.get().defaultBlockState();
            }
            if (roll < CORE_BULKY_WASTE_CHANCE + CORE_BALE_CHANCE) {
                return RCBlocks.COMPACTED_BALE.get().defaultBlockState();
            }
        }
        return RCBlocks.GARBAGE_BLOCK.get().defaultBlockState();
    }
}
