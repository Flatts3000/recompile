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
 * A stack of salvaged steel on the demolition-yard floor (demolition_yard_spec.md S3): the survival source
 * of Steel I-Beams, Reinforced Concrete and copper pipe.
 *
 * <p><b>Modelled on how demolition steel actually sits on a site</b>, from reference photography rather than
 * invention. Three things that reference settles, each of which an earlier attempt got wrong:
 *
 * <ul>
 *   <li><b>It is a low, tight, mostly-parallel stack</b> - long members lying flat, layered two or three
 *       deep, overhanging each other at different lengths with a slight fan. It is salvage that has been
 *       gathered, not debris that fell. A dome of randomly-axised blocks (the first attempt) reads as
 *       confetti, and scattered runs (the second) read as litter.</li>
 *   <li><b>Members share a dominant axis.</b> This is also what {@link SteelBeamBlock} is built for: it
 *       draws the run it belongs to, so beams sharing an axis render as continuous members. Randomising the
 *       axis per block actively defeats the block's own design.</li>
 *   <li><b>The concrete rubble is a SEPARATE heap nearby</b>, not mixed through the steel. On a real site
 *       the ferrous salvage is sorted away from the broken deck.</li>
 * </ul>
 *
 * <p>Copper pipe stacks in with the steel, which is both what the reference shows and quietly useful: pipe
 * is a Cupola Furnace ingredient.
 */
public class SteelStackFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_LAYERS = 2;
    private static final int MAX_LAYERS = 3;
    private static final int MIN_PER_LAYER = 2;
    private static final int MAX_PER_LAYER = 4;
    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 9;
    /** How often a member lies across the stack instead of along it - the fan in the reference. */
    private static final float CROSSWISE = 0.2F;
    /** How much of the stack is copper pipe rather than steel. */
    private static final float PIPE_SHARE = 0.15F;

    public SteelStackFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** The first air block sitting on something solid, or -1 if this column has no usable ground. */
    private static int groundY(WorldGenLevel level, BlockPos column) {
        for (int dy = 2; dy >= -4; dy--) {
            BlockPos at = column.offset(0, dy, 0);
            if (level.getBlockState(at).isAir() && !level.getBlockState(at.below()).isAir()) {
                return at.getY();
            }
        }
        return Integer.MIN_VALUE;
    }

    private static BlockState member(Direction.Axis axis, RandomSource random) {
        if (random.nextFloat() < PIPE_SHARE) {
            return RCBlocks.COPPER_PIPE.get().defaultBlockState();
        }
        return RCBlocks.STEEL_I_BEAM.get().defaultBlockState()
            .setValue(SteelBeamBlock.AXIS, axis)
            .setValue(axis == Direction.Axis.X ? SteelBeamBlock.X : SteelBeamBlock.Z, true);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        int base = groundY(level, origin);
        if (base == Integer.MIN_VALUE) {
            return false;
        }
        Direction.Axis stackAxis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
        Direction.Axis crossAxis = stackAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;

        boolean placedAny = false;
        int layers = MIN_LAYERS + random.nextInt(MAX_LAYERS - MIN_LAYERS + 1);
        for (int layer = 0; layer < layers; layer++) {
            // Higher layers are narrower, so the stack tapers the way a real one does.
            int perLayer = Math.max(1,
                MIN_PER_LAYER + random.nextInt(MAX_PER_LAYER - MIN_PER_LAYER + 1) - layer);
            for (int m = 0; m < perLayer; m++) {
                boolean crosswise = random.nextFloat() < CROSSWISE;
                Direction.Axis axis = crosswise ? crossAxis : stackAxis;
                BlockState state = member(axis, random);
                int length = MIN_LENGTH + random.nextInt(MAX_LENGTH - MIN_LENGTH + 1);

                // Offset across the stack so members lie beside each other, and along it so their ends
                // overhang unevenly - the ragged end of the reference stack.
                int across = random.nextInt(4) - 1;
                int along = random.nextInt(5) - 2;
                for (int i = 0; i < length; i++) {
                    int dx = axis == Direction.Axis.X ? along + i : across;
                    int dz = axis == Direction.Axis.X ? across : along + i;
                    BlockPos pos = new BlockPos(origin.getX() + dx, base + layer, origin.getZ() + dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, state, 2);
                        placedAny = true;
                    }
                }
            }
        }

        // The broken deck, sorted off to one side as a separate heap.
        BlockPos heap = new BlockPos(
            origin.getX() + (random.nextBoolean() ? 6 : -6) + random.nextInt(3) - 1,
            origin.getY(),
            origin.getZ() + (random.nextBoolean() ? 6 : -6) + random.nextInt(3) - 1);
        placedAny |= placeDeckHeap(level, heap, random);
        return placedAny;
    }

    /**
     * The broken deck heap that sits beside a stack, sorted away from the steel the way a real site keeps
     * ferrous salvage apart from rubble.
     *
     * <p>A separate entry point because it lands several blocks off the feature's origin, which puts it
     * outside the shared {@code empty_5x5x5} GameTest plot - and a test may not write ground outside its own
     * plot without corrupting its neighbours. This lets a test drive the real code at a position it owns.
     *
     * <p>Worth guarding: this is the ONLY survival source of Reinforced Concrete, hence of the concrete the
     * Cupola Furnace is built from. If it stops placing, iron silently becomes unreachable.
     */
    public static boolean placeDeckHeap(WorldGenLevel level, BlockPos centre, RandomSource random) {
        BlockState deck = RCBlocks.REINFORCED_CONCRETE.get().defaultBlockState();
        BlockState rubble = RCBlocks.RUBBLE.get().defaultBlockState();
        boolean placedAny = false;
        for (int i = 0; i < 5 + random.nextInt(6); i++) {
            BlockPos column = new BlockPos(
                centre.getX() + random.nextInt(5) - 2, centre.getY(), centre.getZ() + random.nextInt(5) - 2);
            int y = groundY(level, column);
            if (y == Integer.MIN_VALUE) {
                continue;
            }
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, random.nextFloat() < 0.7F ? deck : rubble, 2);
                placedAny = true;
            }
        }
        return placedAny;
    }
}
