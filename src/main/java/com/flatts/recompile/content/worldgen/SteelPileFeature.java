package com.flatts.recompile.content.worldgen;

import com.flatts.recompile.content.block.SteelBeamBlock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A collapsed heap of structural steel and broken deck on the demolition-yard floor
 * (demolition_yard_spec.md S3): where a building came down, and the survival source of both Steel I-Beams
 * and Reinforced Concrete.
 *
 * <p>Shaped like {@link RubblePileFeature}'s dome but deliberately <b>sparse</b>. A solid mound of steel
 * would read as a manufactured block of metal; a collapse is tangle and voids, so roughly a third of the
 * dome is left as air and the rest is mixed.
 *
 * <p>Beams are placed with a <b>random axis</b>, which is what makes the tangle read. A Steel I-Beam draws
 * the run it belongs to (see {@link SteelBeamBlock}), so a heap of default-state beams would all be upright
 * poles in neat columns. Setting the axis per block gives uprights and girders lying across each other.
 * Note these are set without neighbour updates, so each block keeps exactly the orientation chosen here
 * rather than resolving into one continuous lattice - which is the point: this is wreckage, not a frame.
 */
public class SteelPileFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_HEIGHT = 1;
    private static final int MAX_HEIGHT = 3;
    private static final int MIN_WIDTH = 3;
    private static final int MAX_WIDTH = 6;

    /** How much of the dome is actually filled. The rest is the voids a collapse leaves. */
    private static final float FILL = 0.62F;
    /** Of what IS filled, how much is steel. The remainder is the deck that fell with it. */
    private static final float STEEL_SHARE = 0.55F;
    /** Of the non-steel, how much is intact deck rather than loose rubble. */
    private static final float CONCRETE_SHARE = 0.6F;

    public SteelPileFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** A beam lying on a random axis - upright, or a girder running one way or the other. */
    private static BlockState tangledBeam(RandomSource random) {
        BlockState beam = RCBlocks.STEEL_I_BEAM.get().defaultBlockState();
        return switch (random.nextInt(3)) {
            case 0 -> beam.setValue(SteelBeamBlock.AXIS, Direction.Axis.X)
                .setValue(SteelBeamBlock.X, true);
            case 1 -> beam.setValue(SteelBeamBlock.AXIS, Direction.Axis.Z)
                .setValue(SteelBeamBlock.Z, true);
            default -> beam;   // upright, the pole form
        };
    }

    private static BlockState debris(RandomSource random) {
        if (random.nextFloat() < STEEL_SHARE) {
            return tangledBeam(random);
        }
        if (random.nextFloat() < CONCRETE_SHARE) {
            return RCBlocks.REINFORCED_CONCRETE.get().defaultBlockState();
        }
        return RCBlocks.RUBBLE.get().defaultBlockState();
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
                int column = (int) Math.round(height * (1.0 - dist / radius));
                for (int dy = 0; dy <= column; dy++) {
                    if (random.nextFloat() > FILL) {
                        continue;   // the voids
                    }
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, debris(random), 2);
                        placedAny = true;
                    }
                }
            }
        }
        return placedAny;
    }
}
