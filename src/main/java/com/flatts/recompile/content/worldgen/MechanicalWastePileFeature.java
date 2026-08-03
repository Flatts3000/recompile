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
 * A heap of Mechanical Waste on the demolition-yard floor ({@code docs/gem_tier_spec.md} Phase 1): the
 * gem tier's found half.
 *
 * <p>Deliberately <b>smaller and taller</b> than {@link RubblePileFeature}, which spreads wide and low.
 * Rubble is debris scattered by demolition; this is machinery that was stacked somewhere and collapsed,
 * so it reads as a distinct object rather than as more of the same floor. Since the two generate in the
 * same biome step, looking different from ten blocks away is what makes the valuable one findable at all.
 *
 * <p>Density is the placed-feature count, and it is a third of rubble's.
 */
public class MechanicalWastePileFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_HEIGHT = 2;
    private static final int MAX_HEIGHT = 5;
    private static final int MIN_WIDTH = 2;
    private static final int MAX_WIDTH = 4;

    public MechanicalWastePileFeature() {
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
        BlockState waste = RCBlocks.MECHANICAL_WASTE.get().defaultBlockState();

        boolean placedAny = false;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (dist > radius) {
                    continue;
                }
                int column = (int) Math.round(height * (1.0 - dist / (radius + 1.0)));
                for (int dy = 0; dy <= column; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.getBlockState(pos).isAir()) {
                        // Flag 2: skip neighbour updates, so the stack does not collapse as it is built.
                        level.setBlock(pos, waste, 2);
                        placedAny = true;
                    }
                }
            }
        }
        return placedAny;
    }
}
