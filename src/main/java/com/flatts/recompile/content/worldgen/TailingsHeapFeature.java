package com.flatts.recompile.content.worldgen;

import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A uranium mill tailings impoundment: one broad flat-topped pile with a decant pond on it, a sloped
 * skirt, and drums abandoned at the toe (#285).
 *
 * <p><b>This is the second shape of this feature, and the first one was wrong in a way only a
 * screenshot could show.</b> It built 5-11 wide cakes at four per chunk: a flat cap one block wide at
 * the centre, a single sharp step down, and a drum perched on the centre spire. Owner review
 * (2026-08-23) called it cupcakes with a candle, and every part of that was earned - at radius 2-3 the
 * {@code dist <= radius} circle test even produces a literal plus sign, so the silhouettes read as
 * algorithmic on sight. The census numbers that shipped it were all correct and could not see any of
 * it.
 *
 * <p><b>The reference is Moab and Church Rock: ONE enormous impoundment, not a field of mounds.</b> A
 * real site is a single engineered pile hundreds of metres across with a pale turquoise decant pond on
 * top, ringed by a barren stained zone. So this is now few and huge - roughly one per four chunks at
 * 21 to 33 blocks across - and the flat top is kept, because on a real impoundment the flat top is
 * correct. It was the SCALE that made it read as a cake, never the flatness.
 *
 * <p>Four things carry the shape, and each exists to kill a specific tell:
 *
 * <ul>
 *   <li><b>A lobed outline.</b> The radius is modulated by two sine harmonics at random phase, so no
 *       two impoundments share an outline and none of them is a circle. This is what removes the plus
 *       signs and the identical rims.
 *   <li><b>A skirt at the angle of repose.</b> The plateau runs to {@code innerRadius} and then ramps
 *       down over a run DERIVED from the height rather than picked, so the side slope is constant
 *       whatever the pile's size. Loose spoil stands near 35 degrees and that is what {@link #SLOPE}
 *       is.
 *   <li><b>A decant pond.</b> Tailings are pumped in as a slurry and the process water pools on top.
 *       Cut one block below the plateau, so the rim stands above it on every side - which is both what
 *       makes it read as a basin and why it cannot flow, since every neighbour at the water's own
 *       level is tailings.
 *       It is plain water: the colour is the biome's {@code water_color}, which is what makes it read
 *       as process water rather than as a lake. Deliberately NOT the mod's Leachate block - leachate
 *       is rain drained through refuse, which is why it is sprawl-only (owner, 2026-08-05).
 *   <li><b>Drums at the toe, in clusters.</b> Nobody carries a drum to the summit. They are dumped at
 *       the bottom and left, so they cluster, and ones that landed on the skirt end up half sunk in
 *       it. Placement walks up to the local surface, which produces both readings for free.
 * </ul>
 */
public class TailingsHeapFeature extends Feature<NoneFeatureConfiguration> {

    // BROAD AND LOW, and the ratio is the point. Moab's pile is about 40 m over 500 m across - roughly
    // 1:12. The first pass at this shape used radius 8-12 against height 4-6, which is nearer 1:4, and
    // it cost the pond: with the skirt eating radius at the angle of repose there was only enough
    // plateau left to hold one about 27% of the time, and a defining feature cannot be a 1-in-4.
    // Measured, not guessed - a fresh world's first census found tailings, stain and drums correct and
    // zero water anywhere. These numbers put a pond on about 90% of piles.
    private static final int MIN_RADIUS = 10;
    private static final int MAX_RADIUS = 16;
    private static final int MIN_HEIGHT = 3;
    private static final int MAX_HEIGHT = 5;

    /**
     * Rise over run on the skirt: loose spoil stands at its angle of repose, near 35 degrees. The
     * skirt's WIDTH is derived from this and the height rather than chosen, so a tall pile gets a
     * long slope and a short one gets a stub, and neither can end up as a cliff.
     */
    private static final double SLOPE = 0.7;

    /** Clusters of drums abandoned at the toe, and how many drums each holds. */
    private static final int MIN_CLUSTERS = 1;
    private static final int MAX_CLUSTERS = 3;
    private static final int MIN_DRUMS = 2;
    private static final int MAX_DRUMS = 5;

    public TailingsHeapFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);

        double innerRadius = plateauRadius(radius, height);

        // The outline. Two harmonics at random phase turn the circle into a lobed blob, which is the
        // single biggest difference between this and the first version.
        double phaseA = random.nextDouble() * Math.PI * 2.0;
        double phaseB = random.nextDouble() * Math.PI * 2.0;

        double pondRadius = pondRadius(radius, height);

        BlockState tailings = RCBlocks.MILL_TAILINGS.get().defaultBlockState();
        BlockState stain = RCBlocks.STAINED_GROUND.get().defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();

        boolean placedAny = false;
        int reach = radius + 2;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                double edge = outlineAt(dx, dz, radius, phaseA, phaseB);

                // The stain reaches one ring past the toe, so contamination is visible around the
                // edge rather than only where blocks stand.
                //
                // ONLY OVER THE BIOME'S OWN GROUND, and that guard is load-bearing. The first version
                // tested isSolidRender() and painted whatever it found, which is the region's most
                // common case rather than an edge one: WORLD_SURFACE_WG is updated by every setBlock
                // during decoration, so a later pile landing on an earlier one has its origin pushed
                // up onto that pile - and its whole stain disc then converts the neighbour's MILL
                // TAILINGS into dressing. That turns the region's only uranium block into a block
                // with no loot at all, and buries a plate of stain mid-pile. Caught in review of
                // #286.
                if (dist <= edge + 1.5) {
                    BlockPos ground = origin.offset(dx, -1, dz);
                    BlockState under = level.getBlockState(ground);
                    if (under.is(Blocks.COARSE_DIRT) || under.is(RCBlocks.STAINED_GROUND.get())) {
                        level.setBlock(ground, stain, 2);
                    }
                }
                if (dist > edge) {
                    continue;
                }

                int column = columnAt(dist, innerRadius, edge, height);
                if (column < 0) {
                    continue;
                }

                // The pond is cut ONE BLOCK BELOW the plateau, so the rim stands above it on every
                // side. That is what makes it a basin rather than a puddle, and it is also why the
                // water cannot go anywhere: every neighbour at its own level is tailings.
                boolean pond = pondRadius > 0 && dist <= pondRadius;
                int solidTop = pond ? column - 2 : column;
                for (int dy = 0; dy <= solidTop; dy++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, tailings, 2);
                        placedAny = true;
                    }
                }
                if (pond) {
                    BlockPos pos = origin.offset(dx, column - 1, dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, water, 2);
                    }
                }
            }
        }

        if (placedAny) {
            scatterDrums(level, origin, random, radius, height, phaseA, phaseB);
        }
        return placedAny;
    }

    /**
     * How much flat top is left once the skirt has taken its share. The skirt is as wide as the height
     * demands at the angle of repose, so this falls as a pile gets taller and rises as it gets broader.
     *
     * <p>Package-visible because the pond depends on it and the pond is what silently did not generate.
     */
    static double plateauRadius(int radius, int height) {
        return Math.max(2.0, radius - height / SLOPE);
    }

    /**
     * The decant pond's radius, or -1 where the plateau is too small to hold one with a rim left over.
     *
     * <p><b>This is the function that shipped a pond on barely a quarter of piles.</b> With radius 8-12
     * against height 4-6 the skirt ate almost the whole footprint, so most piles came out with a
     * plateau under the threshold and no water at all - and nothing failed, because a pile with no pond
     * is a perfectly valid pile. It took a census of a real world finding zero water to see it.
     * {@code a_decant_pond_is_not_a_coin_flip} pins the rate now.
     */
    static double pondRadius(int radius, int height) {
        double plateau = plateauRadius(radius, height);
        return plateau >= 4.0 ? plateau - 2.0 : -1.0;
    }

    /** The narrowest and widest a pile can be, for the tests that sweep every combination. */
    static int[] radiusRange() {
        return new int[] {MIN_RADIUS, MAX_RADIUS};
    }

    /** The lowest and highest a pile can be, for the tests that sweep every combination. */
    static int[] heightRange() {
        return new int[] {MIN_HEIGHT, MAX_HEIGHT};
    }

    /**
     * The lobed outline: the base radius modulated by two harmonics, so the edge is organic and every
     * impoundment's is different. Amplitudes are small on purpose - this is meant to read as an eroded
     * rim, not as a starfish.
     */
    private static double outlineAt(int dx, int dz, int radius, double phaseA, double phaseB) {
        if (dx == 0 && dz == 0) {
            return radius;
        }
        double angle = Math.atan2(dz, dx);
        return radius * (1.0 + 0.13 * Math.sin(3.0 * angle + phaseA)
            + 0.08 * Math.sin(5.0 * angle + phaseB));
    }

    /**
     * How many blocks stand on a column: full height across the plateau, then a straight ramp down the
     * skirt. Returns the top offset, so 0 is a single block and -1 is nothing at all.
     */
    private static int columnAt(double dist, double innerRadius, double edge, int height) {
        if (dist <= innerRadius) {
            return height;
        }
        double run = Math.max(1.0E-4, edge - innerRadius);
        double t = (dist - innerRadius) / run;
        return (int) Math.round(height * (1.0 - t));
    }

    /**
     * Drums abandoned at the toe. Clustered, because they were unloaded together and left, and never
     * on the summit - the first version put one on the centre spire of every pile, which is the tell
     * that made the whole feature read as decorated cake.
     */
    private static void scatterDrums(WorldGenLevel level, BlockPos origin, RandomSource random,
            int radius, int height, double phaseA, double phaseB) {
        BlockState drum = RCBlocks.WASTE_DRUM.get().defaultBlockState();
        int clusters = MIN_CLUSTERS + random.nextInt(MAX_CLUSTERS - MIN_CLUSTERS + 1);
        for (int c = 0; c < clusters; c++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double edge = outlineAt((int) Math.round(Math.cos(angle) * radius),
                (int) Math.round(Math.sin(angle) * radius), radius, phaseA, phaseB);
            // Just outside the toe, on the stain, where a truck would have stopped.
            double at = edge + 0.5 + random.nextDouble() * 1.5;
            int cx = (int) Math.round(Math.cos(angle) * at);
            int cz = (int) Math.round(Math.sin(angle) * at);

            int drums = MIN_DRUMS + random.nextInt(MAX_DRUMS - MIN_DRUMS + 1);
            for (int d = 0; d < drums; d++) {
                int x = cx + random.nextInt(5) - 2;
                int z = cz + random.nextInt(5) - 2;
                BlockPos on = surfaceIn(level, origin.offset(x, 0, z), height);
                if (on != null && level.getBlockState(on).isAir()) {
                    level.setBlock(on, drum, 2);
                }
            }
        }
    }

    /**
     * The first air above the local surface, searched from the ground plane up through the pile's
     * height. A drum that lands beside the toe sits on the ground; one that lands on the skirt sits on
     * the slope and reads as half sunk in it, which is the same thing a real one does.
     *
     * <p>Returns null if the column is open air the whole way - nothing to stand a drum on.
     */
    private static BlockPos surfaceIn(WorldGenLevel level, BlockPos base, int height) {
        BlockPos found = null;
        for (int dy = -1; dy <= height + 1; dy++) {
            BlockPos pos = base.above(dy);
            if (!level.getBlockState(pos).isAir()) {
                found = pos.above();
            }
        }
        return found;
    }
}
