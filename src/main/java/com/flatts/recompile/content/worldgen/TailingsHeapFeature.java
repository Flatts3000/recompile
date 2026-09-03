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
 * top, ringed by a barren stained zone. So this is now few and broad - roughly one per three chunks at
 * 18 to 24 blocks across - and the flat top is kept, because on a real impoundment the flat top is
 * correct. It was the SCALE that made it read as a cake, never the flatness.
 *
 * <p>Four things carry the shape, and each exists to kill a specific tell:
 *
 * <ul>
 *   <li><b>A lobed outline.</b> The radius is modulated by two sine harmonics at random phase, so no
 *       two impoundments share an outline and none of them is a circle. This is what removes the plus
 *       signs and the identical rims.
 *   <li><b>A skirt at the angle of repose.</b> The plateau runs to {@link #plateauRadius} and then
 *       ramps down over a run DERIVED from the height rather than picked, so the side slope is
 *       constant whatever the pile's size. Loose spoil stands near 35 degrees and that is
 *       {@link #SLOPE}.
 *   <li><b>A decant pond.</b> Tailings are pumped in as a slurry and the process water pools on top.
 *       Cut one block below the plateau, so the rim stands above it on every side - which is both what
 *       makes it read as a basin and why it cannot flow. It is plain water: the colour is the biome's
 *       {@code water_color}, which is what makes it read as process water rather than as a lake.
 *       Deliberately NOT the mod's Leachate block - leachate is rain drained through refuse, which is
 *       why it is sprawl-only (owner, 2026-08-05).
 *   <li><b>Drums at the toe, in clusters.</b> Nobody carries a drum to the summit. They are dumped at
 *       the bottom and left, so they cluster, and ones that landed on the skirt end up half sunk in
 *       it.
 * </ul>
 *
 * <p><b>{@link #MAX_REACH} is a hard engine limit, not a taste decision, and it caps everything
 * else.</b> {@code ChunkPyramid} gives {@code ChunkStatus.FEATURES} a {@code blockStateWriteRadius(1)},
 * and {@code WorldGenRegion.ensureCanWrite} compares CHUNK coordinates against it - so a feature may
 * only touch the centre chunk and its eight neighbours. {@code minecraft:in_square} puts the origin
 * anywhere in the centre chunk's local 0-15, so the guaranteed-writable window is 16 blocks from the
 * origin in every direction. Past that, {@code setBlock} is silently REJECTED and logs
 * {@code Detected setBlock in a far chunk} at ERROR through {@code Util.logAndPauseIfInIde}, which also
 * pauses under a debugger. The visible result is a pile sheared flat along a chunk boundary plus a
 * hundred error lines. The first draft of this rewrite had radius 16 with drums thrown to 23 and would
 * have done exactly that; the radius, the lobe amplitude, the stain ring and the drum throw are now all
 * sized so their sum cannot reach 16, and {@link #writable} is a backstop rather than a shear.
 */
public class TailingsHeapFeature extends Feature<NoneFeatureConfiguration> {

    /**
     * The furthest any block may be written from the origin. See the class note: this is
     * {@code ChunkStatus.FEATURES}'s write radius of one chunk, reduced to blocks for the worst-case
     * origin position inside its own chunk.
     */
    static final int MAX_REACH = 16;

    // BROAD AND LOW, and the ratio is the point. Moab's pile is about 40 m over 500 m across - roughly
    // 1:12. The first pass at this shape used radius 8-12 against height 4-6, which is nearer 1:4, and
    // it cost the pond: the skirt ate the footprint at the angle of repose, so only 26% of piles had
    // enough plateau left to hold one. Measured rather than guessed - a fresh world's first census
    // found tailings, stain and drums all correct and zero water anywhere.
    //
    // The upper bound is MAX_REACH, not taste: the longest lobe is radius * (1 + LOBE_A + LOBE_B) and
    // the stain goes STAIN_RING past that, so 12 * 1.21 + 1 = 15.5 is the whole footprint. Raising
    // MAX_RADIUS past 12 starts writing into far chunks, and
    // `the_feature_never_writes_outside_its_allowed_chunks` fails the build if these drift.
    private static final int MIN_RADIUS = 9;
    private static final int MAX_RADIUS = 12;
    private static final int MIN_HEIGHT = 3;
    private static final int MAX_HEIGHT = 4;

    /** Lobe amplitudes. Small on purpose: an eroded rim, not a starfish. */
    private static final double LOBE_A = 0.13;
    private static final double LOBE_B = 0.08;

    /** How far the stain reaches past the toe. */
    private static final double STAIN_RING = 1.0;

    /**
     * Rise over run on the skirt: loose spoil stands at its angle of repose, near 35 degrees. The
     * skirt's WIDTH is derived from this and the height rather than chosen, so a tall pile gets a
     * long slope and a short one gets a stub, and neither can end up as a cliff.
     */
    private static final double SLOPE = 0.7;

    /** How far down a column will hunt for ground before giving up and building where it is. */
    private static final int MAX_DROP = 8;

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
        // The Municipal Aquarium claims its footprint before any feature runs; nothing the yard or
        // the sprawl scatters may stand in it (owner, 2026-09-03: mounds neither). See BuildingHuskFeature.
        if (com.flatts.recompile.content.worldgen.aquarium.AquariumStructure.claims(level, origin)) {
            return false;
        }
        RandomSource random = context.random();

        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
        double innerRadius = plateauRadius(radius, height);
        double pondRadius = pondRadius(radius, height);

        // The outline. Two harmonics at random phase turn the circle into a lobed blob, which is the
        // single biggest difference between this and the first version.
        double phaseA = random.nextDouble() * Math.PI * 2.0;
        double phaseB = random.nextDouble() * Math.PI * 2.0;

        BlockState tailings = RCBlocks.MILL_TAILINGS.get().defaultBlockState();
        BlockState stain = RCBlocks.STAINED_GROUND.get().defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();

        // The pond is levelled off the ORIGIN's ground rather than each column's, so it comes out flat
        // even where the pile is following uneven terrain. A column standing on different ground gets
        // no water at all, which is preferable to a stepped pond that leaks off the side.
        int originBase = groundUnder(level, origin.getX(), origin.getZ(), origin.getY()) + 1;

        boolean placedAny = false;
        for (int dx = -MAX_REACH; dx <= MAX_REACH; dx++) {
            for (int dz = -MAX_REACH; dz <= MAX_REACH; dz++) {
                double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
                double edge = outlineAt(dx, dz, radius, phaseA, phaseB);
                if (dist > edge + STAIN_RING || !writable(dx, dz)) {
                    continue;
                }

                // EVERY COLUMN STANDS ON ITS OWN GROUND. The origin is a single heightmap sample, and
                // WORLD_SURFACE_WG is bumped by earlier decoration - so a pile whose origin landed on
                // an earlier pile would otherwise fill from up there and leave its whole outer skirt
                // hanging in mid-air over clean terrain. Mill Tailings is a FallingBlock placed with
                // flag 2, so nothing collapses it until a neighbour update happens to arrive. At the
                // old radius of 5 that overhang was cosmetic; at this size it is a floating ring 20
                // blocks across.
                int ground = groundUnder(level, origin.getX() + dx, origin.getZ() + dz, origin.getY());
                int base = ground + 1;

                // The stain reaches one ring past the toe, so contamination is visible around the edge
                // rather than only where blocks stand.
                //
                // ONLY OVER THE BIOME'S OWN GROUND, and that guard is load-bearing. The first version
                // tested isSolidRender() and painted whatever it found, which is the region's most
                // common case rather than an edge one: a later pile landing on an earlier one has its
                // origin pushed up onto that pile, and its whole stain disc then converts the
                // neighbour's MILL TAILINGS into dressing. That turns the region's only uranium block
                // into a block with no loot at all. Caught in review of #286.
                BlockPos groundPos = new BlockPos(origin.getX() + dx, ground, origin.getZ() + dz);
                BlockState under = level.getBlockState(groundPos);
                if (under.is(Blocks.COARSE_DIRT) || under.is(RCBlocks.STAINED_GROUND.get())) {
                    level.setBlock(groundPos, stain, 2);
                }
                if (dist > edge) {
                    continue;
                }

                int column = columnAt(dist, innerRadius, edge, height);

                // The pond is cut ONE BLOCK BELOW the plateau, so the rim stands above it on every
                // side. That is what makes it a basin rather than a puddle, and it is also why the
                // water cannot go anywhere: every neighbour at its own level is tailings.
                boolean pond = pondRadius > 0 && dist <= pondRadius && base == originBase;
                int solidTop = pond ? column - 2 : column;
                for (int dy = 0; dy <= solidTop; dy++) {
                    BlockPos pos = new BlockPos(origin.getX() + dx, base + dy, origin.getZ() + dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, tailings, 2);
                        placedAny = true;
                    }
                }
                if (pond) {
                    BlockPos pos = new BlockPos(
                        origin.getX() + dx, base + column - 1, origin.getZ() + dz);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, water, 2);
                    }
                }
            }
        }

        if (placedAny) {
            scatterDrums(level, origin, random, radius, phaseA, phaseB);
        }
        return placedAny;
    }

    /** Whether a block offset from the origin is inside the window this feature may write to. */
    static boolean writable(int dx, int dz) {
        return Math.abs(dx) <= MAX_REACH && Math.abs(dz) <= MAX_REACH;
    }

    /**
     * The Y of the surface a column stands on, hunted downward from the origin plane. Returns the
     * lowest searched Y if it finds nothing, so a pile over a hole builds rather than aborting.
     */
    private static int groundUnder(WorldGenLevel level, int x, int z, int originY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, originY, z);
        for (int drop = 1; drop <= MAX_DROP; drop++) {
            cursor.setY(originY - drop);
            if (!level.getBlockState(cursor).isAir()) {
                return originY - drop;
            }
        }
        return originY - MAX_DROP;
    }

    /**
     * How much flat top is left once the skirt has taken its share. The skirt is as wide as the height
     * demands at the angle of repose, so this falls as a pile gets taller and rises as it gets broader.
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
        return plateau >= 3.0 ? plateau - 1.5 : -1.0;
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
     * The furthest from the origin anything is written: the longest possible lobe, plus the stain ring
     * that reaches past it. What {@link #MAX_REACH} has to contain.
     */
    static double maxFootprint() {
        return MAX_RADIUS * (1.0 + LOBE_A + LOBE_B) + STAIN_RING;
    }

    /** The lobed radius in the direction of a cell. */
    private static double outlineAt(int dx, int dz, int radius, double phaseA, double phaseB) {
        if (dx == 0 && dz == 0) {
            return radius;
        }
        return outlineTowards(Math.atan2(dz, dx), radius, phaseA, phaseB);
    }

    /** The lobed radius along a bearing. Amplitudes are small: an eroded rim, not a starfish. */
    private static double outlineTowards(double angle, int radius, double phaseA, double phaseB) {
        return radius * (1.0 + LOBE_A * Math.sin(3.0 * angle + phaseA)
            + LOBE_B * Math.sin(5.0 * angle + phaseB));
    }

    /**
     * How many blocks stand on a column: full height across the plateau, then a straight ramp down the
     * skirt. Returns the top offset, so 0 is a single block.
     */
    static int columnAt(double dist, double innerRadius, double edge, int height) {
        if (dist <= innerRadius) {
            return height;
        }
        double run = Math.max(1.0E-4, edge - innerRadius);
        double t = Math.min(1.0, (dist - innerRadius) / run);
        return (int) Math.round(height * (1.0 - t));
    }

    /**
     * Drums abandoned at the toe. Clustered, because they were unloaded together and left, and never on
     * the summit - the first version put one on the centre spire of every pile, which is the tell that
     * made the whole feature read as decorated cake.
     *
     * <p>Sampled by BEARING AND DISTANCE rather than as a box around a cluster centre. The box version
     * threw drums up to four blocks past the toe, which put a good share of them on clean coarse dirt
     * outside the stain - contradicting the sentence above it - and threw the furthest ones clean out
     * of the writable window. Landing them in a band straddling the toe fixes both at once: they sit on
     * the contaminated ring by construction, and the furthest one can reach is a lobe plus a block.
     */
    private static void scatterDrums(WorldGenLevel level, BlockPos origin, RandomSource random,
            int radius, double phaseA, double phaseB) {
        BlockState drum = RCBlocks.WASTE_DRUM.get().defaultBlockState();
        int clusters = MIN_CLUSTERS + random.nextInt(MAX_CLUSTERS - MIN_CLUSTERS + 1);
        for (int c = 0; c < clusters; c++) {
            double bearing = random.nextDouble() * Math.PI * 2.0;
            int drums = MIN_DRUMS + random.nextInt(MAX_DRUMS - MIN_DRUMS + 1);
            for (int d = 0; d < drums; d++) {
                // A short arc and a band across the toe: tight enough to read as one dumping, wide
                // enough that they are not in a line.
                double angle = bearing + (random.nextDouble() - 0.5) * 0.7;
                double edge = outlineTowards(angle, radius, phaseA, phaseB);
                double at = edge - 1.0 + random.nextDouble() * (1.0 + STAIN_RING);
                int dx = (int) Math.round(Math.cos(angle) * at);
                int dz = (int) Math.round(Math.sin(angle) * at);
                if (!writable(dx, dz)) {
                    continue;
                }
                BlockPos on = surfaceIn(level, origin.getX() + dx, origin.getZ() + dz, origin.getY());
                if (on != null && level.getBlockState(on).isAir()) {
                    level.setBlock(on, drum, 2);
                }
            }
        }
    }

    /**
     * The first air above the local surface. A drum that lands beside the toe sits on the ground; one
     * that lands on the skirt sits on the slope and reads as half sunk in it, which is what a real one
     * does.
     */
    private static BlockPos surfaceIn(WorldGenLevel level, int x, int z, int originY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, originY, z);
        BlockPos found = null;
        for (int dy = -MAX_DROP; dy <= MAX_HEIGHT + 1; dy++) {
            cursor.setY(originY + dy);
            if (!level.getBlockState(cursor).isAir()) {
                found = new BlockPos(x, originY + dy + 1, z);
            }
        }
        return found;
    }
}
