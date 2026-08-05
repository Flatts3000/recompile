package com.flatts.recompile.content.worldgen;

import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A shallow pool of leachate (issue #156, design I-8): rain that has drained down through refuse and
 * pooled at a low point, which is the most characteristic thing a landfill actually produces.
 *
 * <p>Placed sparingly in the household sprawl and the demolition yard. Deliberately a feature rather
 * than a region (owner, 2026-08-05) - leachate forms at every dump, so scattering it is truer than
 * concentrating it somewhere you have to travel to.
 *
 * <p><b>The pool is dug, not poured.</b> The feature excavates a shallow basin and fills it, rather
 * than dropping a slab of fluid on the surface. Two reasons, and the second is the one that bites:
 * a puddle sitting on flat ground reads as a texture error, and unsupported fluid <i>flows</i> - a
 * source block placed on an open plain would spread until it found an edge, so the basin is what
 * makes the pool a pool. Every cell is checked for a floor before it is filled.
 *
 * <p>Pools bind themselves to the ground they eat. Only the coarse-dirt family and Mound Ground are
 * excavated, so a pool never carves into a mound, a build, or a machine.
 */
public class LeachatePoolFeature extends Feature<NoneFeatureConfiguration> {

    /** Radius bounds. Small on purpose: this is a puddle in a landfill, not a lake. */
    private static final int MIN_RADIUS = 2;
    private static final int MAX_RADIUS = 4;

    /** How far below the rim the deepest point sits. One block of depth reads at eye level. */
    private static final int DEPTH = 1;

    /** How ragged the edge is. 0 would give a perfect circle, which nothing in a dump is. */
    private static final float EDGE_NOISE = 0.35F;

    public LeachatePoolFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);

        BlockPos surface = surfaceAt(level, origin);
        if (surface == null) {
            return false;
        }

        BlockState leachate = RCBlocks.LEACHATE.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean placed = false;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq > radius * radius) {
                    continue;
                }
                // Ragged rim: cells near the edge drop out at random, so no pool is a circle.
                if (distSq > (radius - 1) * (radius - 1) && random.nextFloat() < EDGE_NOISE) {
                    continue;
                }

                cursor.set(surface.getX() + dx, surface.getY(), surface.getZ() + dz);
                if (!isDiggable(level.getBlockState(cursor))) {
                    continue;
                }
                // Anything standing on this column - a mound, a plant, a husk - means this cell is
                // spoken for. Carving under it would leave the thing above floating over a pond.
                if (!level.getBlockState(cursor.above()).isAir()) {
                    continue;
                }
                // A cell with no floor beneath it would leak the whole pool into whatever is below.
                if (!level.getBlockState(cursor.below(DEPTH)).isSolidRender()) {
                    continue;
                }

                for (int dy = 0; dy < DEPTH; dy++) {
                    level.setBlock(cursor.below(dy), leachate, 2);
                }
                placed = true;
            }
        }
        return placed;
    }

    /**
     * The top ground block near the origin, or null if there is none worth pooling on.
     *
     * <p>Walks down from a little above the origin the same way {@link MyceliumPatchFeature} does,
     * because the plain has enough roll that the placement heightmap is only approximately right.
     */
    private static BlockPos surfaceAt(WorldGenLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos()
            .set(origin.getX(), origin.getY() + 2, origin.getZ());
        while (cursor.getY() > origin.getY() - 4 && level.getBlockState(cursor).isAir()) {
            cursor.move(Direction.DOWN);
        }
        return isDiggable(level.getBlockState(cursor)) ? cursor.immutable() : null;
    }

    /**
     * Ground a pool may eat: the coarse-dirt family and Mound Ground, nothing else.
     *
     * <p>Mound Ground is included on purpose. It is coarse dirt with a different name, a pool sitting
     * in a retired mound's footprint is exactly the sight this feature is for, and the memory it
     * carries is a count of blocks that belong <i>above</i> it - so drowning one does not corrupt
     * anything. Grass is excluded because healed ground is the player's, and garbage is excluded
     * because a pool must never hollow out a mound.
     */
    private static boolean isDiggable(BlockState state) {
        return state.is(BlockTags.DIRT)
            || state.is(Blocks.COARSE_DIRT)
            || state.is(RCBlocks.MOUND_GROUND.get());
    }
}
