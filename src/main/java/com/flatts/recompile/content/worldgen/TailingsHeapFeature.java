package com.flatts.recompile.content.worldgen;

import com.flatts.recompile.registry.RCBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A heap of Mill Tailings with the odd drum in it, on a stain (#285).
 *
 * <p><b>The region's whole silhouette in one feature.</b> V1 ships no radiation and no detector, so the
 * scatter and the finds carry the identity on their own (spec section 8) - which is why this places all
 * three of the dump's blocks rather than one, and why the placed-feature count is generous. Arriving
 * has to be unmistakable.
 *
 * <p>Modelled on {@link RubblePileFeature}: the same dome profile, because a spoil heap and a rubble
 * pile are the same shape of thing. Three differences, each deliberate:
 *
 * <ul>
 *   <li><b>Wider and lower.</b> Tailings are produced in volumes far too large to contain and were
 *       left in unlined heaps beside rivers - Moab, Church Rock. That reads as a spread, not a spire.
 *   <li><b>It stains the ground it sits on.</b> The rim of the heap becomes Stained Ground, so the
 *       contamination is visible around the edge rather than only where blocks stand. That block
 *       cannot be healed, which is the design rather than a limitation - see StainedGroundBlock.
 *   <li><b>A drum sometimes sits on top.</b> Punctuation: the object that says what the place is.
 * </ul>
 *
 * <p>Vanilla's {@code minecraft:random_patch} was tried first and does not exist under that id in 26.1
 * - every scatter in this mod is a custom feature and only {@code minecraft:ore} is used from vanilla,
 * so this follows the idiom rather than fighting it.
 */
public class TailingsHeapFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_HEIGHT = 1;
    private static final int MAX_HEIGHT = 3;
    private static final int MIN_WIDTH = 5;
    private static final int MAX_WIDTH = 11;

    /** One heap in three carries a drum. Rarer than that and the drum stops reading as part of it. */
    private static final int DRUM_CHANCE = 3;

    public TailingsHeapFeature() {
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

        BlockState tailings = RCBlocks.MILL_TAILINGS.get().defaultBlockState();
        BlockState stain = RCBlocks.STAINED_GROUND.get().defaultBlockState();

        boolean placedAny = false;
        BlockPos peak = null;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (dist > radius) {
                    continue;
                }
                int column = (int) Math.round(height * (1.0 - dist / radius));

                // The stain goes UNDER the heap and one ring beyond it, so contamination is visible
                // around the edge rather than only where blocks stand.
                BlockPos ground = origin.offset(dx, -1, dz);
                if (level.getBlockState(ground).isSolidRender()) {
                    level.setBlock(ground, stain, 2);
                }
                if (column == 0) {
                    continue;
                }
                for (int dy = 0; dy <= column; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, tailings, 2);
                        placedAny = true;
                        if (dx == 0 && dz == 0) {
                            peak = pos;
                        }
                    }
                }
            }
        }

        if (peak != null && random.nextInt(DRUM_CHANCE) == 0) {
            BlockPos on = peak.above();
            if (level.getBlockState(on).isAir()) {
                level.setBlock(on, RCBlocks.WASTE_DRUM.get().defaultBlockState(), 2);
            }
        }
        return placedAny;
    }
}
