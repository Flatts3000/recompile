package com.flatts.recompile.content.worldgen;

import com.flatts.recompile.content.block.RegrowingGroundBlock;
import com.flatts.recompile.content.block.RubbleGroundBlock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A low pile of Rubble on the demolition-yard floor (demolition_yard_spec.md S3(b)): the workaday stone
 * source you sift bare-hand. Lower and wider than a garbage mound - debris spread, not a heap - so it
 * reads as scattered rubble rather than a spire. The same dome profile as {@link MoundFeature}, but a
 * single block (Rubble) and a shallower height range. Density is the placed-feature count.
 */
public class RubblePileFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_HEIGHT = 1;
    private static final int MAX_HEIGHT = 4;
    private static final int MIN_WIDTH = 3;
    private static final int MAX_WIDTH = 7;

    public RubblePileFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        // The Municipal Aquarium claims its footprint before any feature runs; nothing the yard or
        // the sprawl scatters may stand in it (owner, 2026-09-03: mounds neither). See BuildingHuskFeature.
        if (com.flatts.recompile.content.worldgen.aquarium.AquariumStructure.claims(level, origin)) {
            return false;
        }
        RandomSource random = context.random();

        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        int width = MIN_WIDTH + random.nextInt(MAX_WIDTH - MIN_WIDTH + 1);
        double radius = width / 2.0;
        int r = (int) Math.floor(radius);
        BlockState rubble = RCBlocks.STONE_RUBBLE.get().defaultBlockState();

        boolean placedAny = false;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (dist > radius) {
                    continue;
                }
                // Dome profile: tallest at the center, tapering to a 1-block rim.
                int column = (int) Math.round(height * (1.0 - dist / radius));
                writeBed(level, origin.offset(dx, -1, dz), column);
                for (int dy = 0; dy <= column; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, rubble, 2);
                        placedAny = true;
                    }
                }
            }
        }
        return placedAny;
    }

    /**
     * Remember this column, so Phase 5 regrowth can put it back (P1.6-R).
     *
     * <p>{@link MoundFeature#writeBed} is the original and this is the same three rules, because they
     * were all paid for once already on mounds and none of them is specific to garbage:
     *
     * <ul>
     *   <li><b>Overlapping piles take the TALLER.</b> A later pile's rim is a column of 0 and would
     *       otherwise overwrite a tall neighbour's memory, permanently flattening what regrows there -
     *       silent, and invisible until somebody quarries that pile and watches it come back wrong.
     *   <li><b>Only ever replaces ground</b>, which is {@link RegrowingGroundBlock#isBedGround}: coarse
     *       dirt. Writing into another pile's rubble would punch a hole in a stack nobody has touched
     *       yet, and writing into anything else solid replaced a sewer entrance's pad (#432).
     *   <li><b>Stores the block COUNT, not the top offset.</b> The loop above fills
     *       {@code dy = 0..column} inclusive, so a rim cell of column 0 still carries one block.
     *       Storing the offset would build every pile one block short and would leave 0 meaning both
     *       "a one-block rim" and "nothing here" - and 0 has to keep meaning only the second, because
     *       that is what makes a hand-placed block inert.
     * </ul>
     */
    private void writeBed(WorldGenLevel level, BlockPos pos, int column) {
        BlockState existing = level.getBlockState(pos);
        if (existing.getBlock() instanceof RubbleGroundBlock) {
            if (existing.getValue(RubbleGroundBlock.HEIGHT) >= column + 1) {
                return;
            }
        } else if (!RegrowingGroundBlock.isBedGround(existing)) {
            return;
        }
        level.setBlock(pos, RCBlocks.RUBBLE_GROUND.get().defaultBlockState()
            .setValue(RubbleGroundBlock.HEIGHT, Math.min(column + 1, 16)), 2);
    }
}
