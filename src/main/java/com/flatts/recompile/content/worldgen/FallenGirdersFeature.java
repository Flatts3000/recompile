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
 * Girders down on the demolition-yard floor (demolition_yard_spec.md S3): where a frame came apart. The
 * survival source of both Steel I-Beams and Reinforced Concrete.
 *
 * <p><b>Not a pile.</b> A heap is the wrong shape for structural steel and reads as noise: a beam is a long
 * member, and beams fall as runs and get stacked in parallel - they do not mound like gravel. The first
 * version domed them with a random axis per block, which also threw away the thing the block is built to do.
 * {@link SteelBeamBlock} <i>draws the run it belongs to</i>, so a straight line of beams sharing an axis
 * renders as one continuous girder, while randomised neighbours render as confetti.
 *
 * <p>So this places a few straight <b>runs</b>, each a real girder lying where it fell, with broken deck
 * scattered around them and the occasional upright still standing. Blocks are set without neighbour updates
 * so each run keeps exactly the orientation chosen here rather than fusing with its neighbours into a
 * lattice.
 */
public class FallenGirdersFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RUNS = 2;
    private static final int MAX_RUNS = 4;
    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 10;
    private static final int SPREAD = 4;
    /** Gaps along a run: a fallen girder is bent and part-buried, not a clean rail. */
    private static final float RUN_CONTINUITY = 0.85F;

    public FallenGirdersFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** Drops a block onto the first solid ground at or below this column, within a few blocks. */
    private static boolean placeOnGround(WorldGenLevel level, BlockPos pos, BlockState state) {
        for (int dy = 1; dy >= -3; dy--) {
            BlockPos at = pos.offset(0, dy, 0);
            if (level.getBlockState(at).isAir() && !level.getBlockState(at.below()).isAir()) {
                level.setBlock(at, state, 2);
                return true;
            }
        }
        return false;
    }

    private static boolean girder(WorldGenLevel level, BlockPos start, Direction.Axis axis, int length,
            RandomSource random) {
        // One axis for the whole run, so the beams read as a single member rather than a row of stubs.
        BlockState beam = RCBlocks.STEEL_I_BEAM.get().defaultBlockState()
            .setValue(SteelBeamBlock.AXIS, axis)
            .setValue(axis == Direction.Axis.X ? SteelBeamBlock.X : SteelBeamBlock.Z, true);

        boolean placedAny = false;
        for (int i = 0; i < length; i++) {
            if (random.nextFloat() > RUN_CONTINUITY) {
                continue;
            }
            BlockPos pos = axis == Direction.Axis.X ? start.offset(i, 0, 0) : start.offset(0, 0, i);
            placedAny |= placeOnGround(level, pos, beam);
        }
        return placedAny;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        boolean placedAny = false;
        int runs = MIN_RUNS + random.nextInt(MAX_RUNS - MIN_RUNS + 1);
        for (int run = 0; run < runs; run++) {
            Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
            int length = MIN_LENGTH + random.nextInt(MAX_LENGTH - MIN_LENGTH + 1);
            BlockPos start = origin.offset(
                random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, random.nextInt(SPREAD * 2 + 1) - SPREAD);
            placedAny |= girder(level, start, axis, length, random);
        }

        // Broken deck around the wreck: the ONLY survival source of Reinforced Concrete, and therefore of
        // the concrete the Cupola Furnace is built from. If this stops placing, iron becomes unreachable.
        BlockState deck = RCBlocks.REINFORCED_CONCRETE.get().defaultBlockState();
        BlockState rubble = RCBlocks.RUBBLE.get().defaultBlockState();
        int chunks = 3 + random.nextInt(5);
        for (int i = 0; i < chunks; i++) {
            BlockPos pos = origin.offset(
                random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, random.nextInt(SPREAD * 2 + 1) - SPREAD);
            placedAny |= placeOnGround(level, pos, random.nextFloat() < 0.65F ? deck : rubble);
        }

        // One upright left standing, sometimes - the stub of a column the rest tore away from.
        if (random.nextFloat() < 0.4F) {
            BlockPos pos = origin.offset(
                random.nextInt(SPREAD * 2 + 1) - SPREAD, 0, random.nextInt(SPREAD * 2 + 1) - SPREAD);
            BlockState upright = RCBlocks.STEEL_I_BEAM.get().defaultBlockState();
            for (int h = 0; h < 1 + random.nextInt(3); h++) {
                placedAny |= placeOnGround(level, pos.offset(0, h, 0), upright);
            }
        }
        return placedAny;
    }
}
