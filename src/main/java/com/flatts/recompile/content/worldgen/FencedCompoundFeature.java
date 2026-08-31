package com.flatts.recompile.content.worldgen;

import com.flatts.recompile.content.block.ChainLinkFenceBlock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A fenced-off compound (#310): the mod's first boundary, and the thing that makes a patch of ground
 * read as somewhere rather than as terrain.
 *
 * <p><b>A perimeter, not a run.</b> A line of fence in open ground is scenery; a rectangle you have to
 * walk around is a place somebody used to run. The two say different things and only one of them is
 * worth generating.
 *
 * <p><b>In every biome</b> (owner, 2026-08-31). #310's own decision was structures only, on the
 * argument that fencing a compound is what turns a cooling tower or a smokestack into a site. That is
 * overruled here: fences go everywhere. The reasoning survives the overrule - a compound is still what
 * gets placed, it is simply not tied to a landmark - and a compound that happens to enclose one is
 * then a bonus rather than the whole feature. The one biome it is NOT in is the compacted depths,
 * because that dimension is solid from floor to ceiling and a fence needs open ground to stand on.
 *
 * <p><b>A feature, not a structure, and the size follows from that.</b> {@code ChunkStatus.FEATURES}
 * carries {@code blockStateWriteRadius(1)}, so a feature may write about 16 blocks from its origin.
 * Compounds are 9 to 15 a side, which is at most 7 either way from centre - comfortably inside it.
 * Wanting a bigger yard would mean becoming a structure, the way the cooling tower had to.
 *
 * <p><b>The panels connect themselves, and a hand-rolled pass to do it was written and deleted.</b>
 * This repo's Steel I-Beam note says worldgen placement with flag 2 skips neighbour updates and that a
 * frame has to resolve its own joints, which reads as covering this case and does not. That note is
 * about neighbour NOTIFICATIONS. Pane connections come from shape updates, and
 * {@code WorldGenRegion.setBlock} marks a position for post-processing whenever
 * {@code UPDATE_KNOWN_SHAPE} (16) is clear - so flag 2 gets them resolved by the engine during the
 * chunk's post-processing pass. The resolve pass written here on the strength of that note changed
 * nothing, which is only known because disabling it failed no test. Setting bit 16 on the placement
 * below WOULD break it, and {@code a_fenced_compound_is_connected_and_has_a_way_in} is what says so.
 *
 * <p>Three things it does that a plain rectangle would not, all of them free once the block exists:
 *
 * <ul>
 *   <li><b>A cut panel is the way in.</b> A gap on one side, because that is how anyone actually gets
 *       into a fenced yard and it needs no interaction code - a hole somebody cut is a better sentence
 *       than a door somebody installed.
 *   <li><b>Panels are missing.</b> Nothing here has been maintained in forty years, and an unbroken
 *       perimeter would be the one tidy object in the dump.
 *   <li><b>Some compounds are wired.</b> The top course barbed rather than the whole run, so a climb
 *       carries you up the fence and stops at the wire.
 * </ul>
 */
public class FencedCompoundFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_SIDE = 9;
    private static final int MAX_SIDE = 15;

    /** Two courses. A one-block fence is a kerb and a three-block one is a wall. */
    private static final int HEIGHT = 2;

    /** How much of the perimeter has fallen down. */
    private static final float MISSING = 0.18F;

    /** How many compounds were the kind somebody meant to keep people out of. */
    private static final float BARBED = 0.35F;

    /** How much height variation the footprint may have before it is a hill rather than a yard. */
    private static final int MAX_RELIEF = 3;

    public FencedCompoundFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        int width = MIN_SIDE + random.nextInt(MAX_SIDE - MIN_SIDE + 1);
        int depth = MIN_SIDE + random.nextInt(MAX_SIDE - MIN_SIDE + 1);
        int halfW = width / 2;
        int halfD = depth / 2;
        boolean barbed = random.nextFloat() < BARBED;

        // The way in: one side, and a run of two or three panels on it. Picked before anything is
        // placed so the gap is a decision rather than an accident of the missing-panel roll.
        Direction gapSide = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int gapAt = random.nextInt(Math.max(1, (gapSide.getAxis() == Direction.Axis.X ? depth : width) - 3));
        int gapWidth = 2 + random.nextInt(2);

        // FLAT GROUND ONLY, and this was added after watching the first version generate.
        //
        // The feature worked and was nearly invisible. Household sprawl is wall-to-wall mounds, so a
        // perimeter laid on WORLD_SURFACE_WG climbs over and threads between them: measured at one
        // compound, 10 of 45 ground panels had a mound block sitting directly on top and the rest
        // showed in fragments. A fence you cannot see is not a boundary, and "someone fenced this
        // off" is the entire reason the block exists.
        //
        // So the footprint has to be level. That is not a compromise with the mounds - it is where a
        // fenced yard belongs anyway: the open coarse-dirt flats between them, which is also where a
        // player walks. Three blocks of spread tolerates a bit of undulation and rejects a mound.
        if (spread(level, origin, halfW, halfD) > MAX_RELIEF) {
            return false;
        }

        java.util.List<BlockPos> placed = new java.util.ArrayList<>();
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                boolean onEdge = dx == -halfW || dx == halfW || dz == -halfD || dz == halfD;
                if (!onEdge) {
                    continue;
                }
                if (inGap(gapSide, gapAt, gapWidth, dx, dz, halfW, halfD)) {
                    continue;
                }
                if (random.nextFloat() < MISSING) {
                    continue;
                }
                raise(level, origin.getX() + dx, origin.getZ() + dz, barbed, placed);
            }
        }
        // A perimeter that placed almost nothing is not a compound. It happens where the feature lands
        // on a mound face or a cliff, and leaving four posts in a field is worse than leaving nothing.
        if (placed.size() <= width + depth) {
            return false;
        }
        return true;
    }

    /**
     * How much the ground rises and falls across the footprint.
     *
     * <p>Sampled over the whole box rather than the perimeter, because a mound sitting in the MIDDLE
     * of a compound is just as wrong as one on its edge - the fence would ring a hill.
     */
    private static int spread(WorldGenLevel level, BlockPos origin, int halfW, int halfD) {
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,
                    origin.getX() + dx, origin.getZ() + dz);
                low = Math.min(low, y);
                high = Math.max(high, y);
                // Nothing flat enough can still be under consideration once it is this uneven, and
                // the footprint is up to 225 columns.
                if (high - low > MAX_RELIEF) {
                    return high - low;
                }
            }
        }
        return high - low;
    }

    /** Whether this perimeter cell is the hole somebody cut to get in. */
    private static boolean inGap(Direction side, int at, int gapWidth, int dx, int dz,
            int halfW, int halfD) {
        if (side == Direction.NORTH && dz != -halfD) {
            return false;
        }
        if (side == Direction.SOUTH && dz != halfD) {
            return false;
        }
        if (side == Direction.WEST && dx != -halfW) {
            return false;
        }
        if (side == Direction.EAST && dx != halfW) {
            return false;
        }
        int along = side.getAxis() == Direction.Axis.X ? dz + halfD : dx + halfW;
        return along >= at && along < at + gapWidth;
    }

    /**
     * Stand one post of the fence on whatever the ground is here.
     *
     * <p>Per column rather than off the feature origin: the plain is flat but mounds are not, and a
     * perimeter drawn at one height would bury itself on one side and hang in the air on the other.
     *
     * <p>Records what it placed into {@code out}, so the caller can tell a compound from four posts
     * and so the connection pass has a list to walk rather than a box to rescan.
     */
    private static void raise(WorldGenLevel level, int x, int z, boolean barbed,
            java.util.List<BlockPos> out) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        BlockPos foot = new BlockPos(x, y, z);
        if (!level.getBlockState(foot.below()).isSolidRender()) {
            return;
        }
        for (int dy = 0; dy < HEIGHT; dy++) {
            BlockPos at = foot.above(dy);
            if (!level.getBlockState(at).isAir()) {
                break;
            }
            // ONLY THE TOP COURSE IS WIRED. Barbed wire sits on top of a fence, and the block is
            // climbable exactly when it is not barbed - so a wired compound is one you can climb
            // most of the way up and not over, which is the whole point of the variant.
            boolean wire = barbed && dy == HEIGHT - 1;
            BlockState state = RCBlocks.CHAIN_LINK_FENCE.get().defaultBlockState()
                .setValue(ChainLinkFenceBlock.BARBED, wire);
            level.setBlock(at, state, Block.UPDATE_CLIENTS);
            out.add(at);
        }
    }
}
