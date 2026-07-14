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
 * <p>Deliberately simple for the feasibility slice - it sits blocks on the surface
 * relative to the placement origin. On the gently rolling substrate that reads as
 * mounds; per-column heightmap sampling can refine the skirt later if needed.
 */
public class MoundFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 4;

    public MoundFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        int height = radius + random.nextInt(2);
        BlockState garbage = RCBlocks.GARBAGE_BLOCK.get().defaultBlockState();

        boolean placedAny = false;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (dist > radius + 0.5) {
                    continue;
                }
                // Dome profile: tallest at the center, tapering to the rim.
                int column = (int) Math.round(height * (1.0 - dist / (radius + 1.0)));
                for (int dy = 0; dy <= column; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, garbage, 2);
                        placedAny = true;
                    }
                }
            }
        }
        return placedAny;
    }
}
