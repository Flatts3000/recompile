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
 * A small patch of vanilla mycelium with dump mushrooms on it (design P1.9): the
 * forageable food substrate that dots the coarse-dirt plain between mounds. The
 * mycelium replaces the surface dirt block; a few dump mushrooms sprout on top.
 *
 * <p>Vanilla {@code minecraft:mycelium} is safe as the substrate here even though it
 * spreads: {@link net.minecraft.world.level.block.SpreadingSnowyDirtBlock} only
 * propagates onto plain {@code minecraft:dirt}, and this world's surface is
 * coarse dirt, so a patch stays a patch.
 *
 * <p>Mushroom density is the product of three numbers - the placed-feature {@code count}
 * (patches per chunk), the patch area, and {@link #MUSHROOM_CHANCE} - so it climbs
 * fast. At count 6 / chance 0.4 this averaged ~38 mushrooms per chunk, which read as
 * a mushroom field rather than a scavenge. Retune here and in the placed feature.
 */
public class MyceliumPatchFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 3;
    private static final float MUSHROOM_CHANCE = 0.15F;

    public MyceliumPatchFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        int r = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);

        BlockState mycelium = Blocks.MYCELIUM.defaultBlockState();
        BlockState mushroom = RCBlocks.DUMP_MUSHROOM.get().defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        boolean placed = false;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                // Find the top solid block near the surface (terrain has slight roll).
                cursor.set(origin.getX() + dx, origin.getY() + 2, origin.getZ() + dz);
                while (cursor.getY() > origin.getY() - 4 && level.getBlockState(cursor).isAir()) {
                    cursor.move(Direction.DOWN);
                }
                if (!level.getBlockState(cursor).is(BlockTags.DIRT)) {
                    continue; // only convert coarse-dirt ground, never garbage blocks
                }
                // Mycelium dies in the dark; skip covered ground so patches don't
                // immediately revert to dirt under a mound overhang.
                if (!level.getBlockState(cursor.above()).isAir()) {
                    continue;
                }
                level.setBlock(cursor, mycelium, 2);
                placed = true;
                BlockPos above = cursor.above();
                if (level.getBlockState(above).isAir() && random.nextFloat() < MUSHROOM_CHANCE) {
                    level.setBlock(above, mushroom, 2);
                }
            }
        }
        return placed;
    }
}
