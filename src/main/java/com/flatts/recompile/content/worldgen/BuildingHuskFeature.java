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
 * A building husk (demolition_yard_spec.md S3, issue #49): a steel frame stripped to its skeleton, wearing
 * what is left of its concrete floors. The demolition yard's landmark.
 *
 * <p>Procedural rather than an NBT template, because a ruin is the one thing generation genuinely nails -
 * every husk should be a different building rather than the same one stamped repeatedly. It leans on the
 * three cheap cues that make a skeleton read as a building:
 *
 * <ol>
 *   <li><b>A grid.</b> Columns on regular bay spacing is what says "this was built" rather than "this fell
 *       here". It is the single strongest cue and the cheapest.</li>
 *   <li><b>Exposed structure.</b> Girders spanning between column tops at every floor, with the deck only
 *       partly there - a frame you can see through reads as stripped, where a solid box reads as a house.</li>
 *   <li><b>A ragged top.</b> Columns stop at different heights and the upper floors lose most of their deck,
 *       so the building looks torn down rather than unfinished. A flat top reads as construction.</li>
 * </ol>
 *
 * <p>Deck coverage falls off with height, which is what demolition actually looks like: crews take the top
 * floors first, so the lower ones are intact and the upper ones are ribs.
 */
public class BuildingHuskFeature extends Feature<NoneFeatureConfiguration> {

    private static final int BAY = 4;
    private static final int MIN_BAYS = 2;
    private static final int MAX_BAYS = 4;
    private static final int MIN_FLOORS = 2;
    private static final int MAX_FLOORS = 5;
    private static final int FLOOR_HEIGHT = 4;

    public BuildingHuskFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    private static int groundY(WorldGenLevel level, int x, int z, int near) {
        for (int dy = 3; dy >= -5; dy--) {
            BlockPos at = new BlockPos(x, near + dy, z);
            if (level.getBlockState(at).isAir() && !level.getBlockState(at.below()).isAir()) {
                return at.getY();
            }
        }
        return Integer.MIN_VALUE;
    }

    private static BlockState column() {
        return RCBlocks.STEEL_I_BEAM.get().defaultBlockState();
    }

    private static BlockState girder(Direction.Axis axis) {
        return RCBlocks.STEEL_I_BEAM.get().defaultBlockState()
            .setValue(SteelBeamBlock.AXIS, axis)
            .setValue(axis == Direction.Axis.X ? SteelBeamBlock.X : SteelBeamBlock.Z, true);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        int baysX = MIN_BAYS + random.nextInt(MAX_BAYS - MIN_BAYS + 1);
        int baysZ = MIN_BAYS + random.nextInt(MAX_BAYS - MIN_BAYS + 1);
        int floors = MIN_FLOORS + random.nextInt(MAX_FLOORS - MIN_FLOORS + 1);

        int base = groundY(level, origin.getX(), origin.getZ(), origin.getY());
        if (base == Integer.MIN_VALUE) {
            return false;
        }
        int topY = base + floors * FLOOR_HEIGHT;

        // Ragged top: each column line stops somewhere near the top rather than all at once.
        int[][] columnTop = new int[baysX + 1][baysZ + 1];
        for (int gx = 0; gx <= baysX; gx++) {
            for (int gz = 0; gz <= baysZ; gz++) {
                int lost = random.nextInt(FLOOR_HEIGHT + 2);
                columnTop[gx][gz] = topY - lost;
            }
        }

        boolean placedAny = false;
        BlockState deck = RCBlocks.REINFORCED_CONCRETE.get().defaultBlockState();

        // 1. The grid of columns.
        for (int gx = 0; gx <= baysX; gx++) {
            for (int gz = 0; gz <= baysZ; gz++) {
                int x = origin.getX() + gx * BAY;
                int z = origin.getZ() + gz * BAY;
                for (int y = base; y <= columnTop[gx][gz]; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, column(), 2);
                        placedAny = true;
                    }
                }
            }
        }

        // 2. Girders and deck, floor by floor.
        for (int floor = 1; floor <= floors; floor++) {
            int y = base + floor * FLOOR_HEIGHT;
            // Higher floors are stripped harder - crews work top down.
            float deckChance = 0.75F * (1.0F - (floor - 1) / (float) Math.max(1, floors));

            for (int gx = 0; gx <= baysX; gx++) {
                for (int gz = 0; gz <= baysZ; gz++) {
                    int x = origin.getX() + gx * BAY;
                    int z = origin.getZ() + gz * BAY;

                    // Girders span to the next column, if both ends still stand at this height.
                    if (gx < baysX && y <= columnTop[gx][gz] && y <= columnTop[gx + 1][gz]) {
                        for (int i = 1; i < BAY; i++) {
                            BlockPos pos = new BlockPos(x + i, y, z);
                            if (level.getBlockState(pos).isAir()) {
                                level.setBlock(pos, girder(Direction.Axis.X), 2);
                                placedAny = true;
                            }
                        }
                    }
                    if (gz < baysZ && y <= columnTop[gx][gz] && y <= columnTop[gx][gz + 1]) {
                        for (int i = 1; i < BAY; i++) {
                            BlockPos pos = new BlockPos(x, y, z + i);
                            if (level.getBlockState(pos).isAir()) {
                                level.setBlock(pos, girder(Direction.Axis.Z), 2);
                                placedAny = true;
                            }
                        }
                    }

                    // Deck inside the bay, in patches - a floor half torn out, not a checkerboard.
                    if (gx < baysX && gz < baysZ && random.nextFloat() < deckChance + 0.15F) {
                        for (int ix = 1; ix < BAY; ix++) {
                            for (int iz = 1; iz < BAY; iz++) {
                                if (random.nextFloat() > deckChance) {
                                    continue;
                                }
                                BlockPos pos = new BlockPos(x + ix, y, z + iz);
                                if (level.getBlockState(pos).isAir()) {
                                    level.setBlock(pos, deck, 2);
                                    placedAny = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return placedAny;
    }
}
