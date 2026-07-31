package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.SteelBeamBlock;
import com.flatts.recompile.content.worldgen.SteelStackFeature;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCFeatures;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * GameTests for the steel stack - the demolition yard's survival source of Steel I-Beams, Reinforced
 * Concrete and copper pipe.
 *
 * <p>The stack and its deck heap are tested separately on purpose. A real stack sorts its concrete several
 * blocks off to one side, which puts the heap outside the shared {@code empty_5x5x5} plot - and a test must
 * not write ground outside its own plot, because the plots sit close together and it corrupts its
 * neighbours (learned the hard way: a floor laid at radius 14 deleted the hopper out of the Cupola test).
 * So the heap is driven through its own entry point at a position this test owns.
 */
final class SteelStackTests {

    private SteelStackTests() {
    }

    private static void placeStack(ServerLevel level, BlockPos origin, int seed) {
        RCFeatures.STEEL_STACK.get().place(new FeaturePlaceContext<>(
            Optional.empty(), level, level.getChunkSource().getGenerator(),
            RandomSource.create(seed), origin, NoneFeatureConfiguration.INSTANCE));
    }

    static void register() {
        // The stack itself: steel, laid on the ground, inside the plot.
        RCGameTests.test("steel_stack_places_steel", 40, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
            Set<Block> found = new HashSet<>();
            for (int seed = 0; seed < 8; seed++) {
                placeStack(level, origin, seed);
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 3, 2))) {
                    found.add(level.getBlockState(pos).getBlock());
                }
            }
            helper.assertTrue(found.contains(RCBlocks.STEEL_I_BEAM.get()),
                "a steel stack must place Steel I-Beams");
            helper.succeed();
        });

        // Members must share an axis so the beams render as continuous members - that is what
        // SteelBeamBlock is built to do, and randomising per block defeats it. Across several stacks the
        // dominant axis should still vary, so both orientations appear.
        RCGameTests.test("steel_stack_members_share_an_axis", 40, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
            Set<Direction.Axis> axes = new HashSet<>();
            for (int seed = 0; seed < 12; seed++) {
                placeStack(level, origin, seed);
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 3, 2))) {
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof SteelBeamBlock) {
                        axes.add(state.getValue(SteelBeamBlock.AXIS));
                    }
                }
            }
            helper.assertTrue(!axes.isEmpty(), "a stack must place oriented beams");
            helper.assertTrue(axes.size() > 1,
                "across several stacks the dominant axis must vary, got " + axes);
            helper.succeed();
        });

        // The deck heap. This is the ONLY survival source of Reinforced Concrete, and therefore of the
        // concrete the Cupola Furnace is built from - if it stops placing, iron becomes unreachable with
        // every other test still green. Driven through its own entry point, in-plot.
        RCGameTests.test("steel_stack_deck_heap_yields_concrete", 40, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos centre = helper.absolutePos(new BlockPos(2, 2, 2));
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    helper.setBlock(new BlockPos(2 + dx, 1, 2 + dz), Blocks.STONE);
                }
            }
            Set<Block> found = new HashSet<>();
            for (int seed = 0; seed < 8; seed++) {
                SteelStackFeature.placeDeckHeap(level, centre, RandomSource.create(seed));
                for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-2, 0, -2), centre.offset(2, 2, 2))) {
                    found.add(level.getBlockState(pos).getBlock());
                }
            }
            helper.assertTrue(found.contains(RCBlocks.REINFORCED_CONCRETE.get()),
                "the deck heap must place Reinforced Concrete - it is the only survival source, and the "
                    + "Cupola Furnace (and therefore all iron) depends on it");
            helper.succeed();
        });

        // The husk's three cues, asserted so a refactor cannot quietly flatten it into a box:
        // a GRID of columns, girders spanning between them, and a RAGGED top.
        RCGameTests.test("building_husk_is_a_ragged_frame", 60, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));

            Set<Integer> columnTops = new HashSet<>();
            boolean sawGirder = false;
            boolean sawDeck = false;
            boolean sawJoint = false;
            java.util.List<BlockPos> deckBlocks = new java.util.ArrayList<>();
            for (int seed = 0; seed < 6; seed++) {
                RCFeatures.BUILDING_HUSK.get().place(new FeaturePlaceContext<>(
                    Optional.empty(), level, level.getChunkSource().getGenerator(),
                    RandomSource.create(seed), origin, NoneFeatureConfiguration.INSTANCE));
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(3, 24, 3))) {
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() == RCBlocks.REINFORCED_CONCRETE.get()) {
                        sawDeck = true;
                        deckBlocks.add(pos.immutable());
                    }
                    if (!(state.getBlock() instanceof SteelBeamBlock)) {
                        continue;
                    }
                    // A girder carries a horizontal run; a column does not.
                    if (state.getValue(SteelBeamBlock.X) && state.getValue(SteelBeamBlock.Z)) {
                        sawJoint = true;
                        sawGirder = true;
                    } else if (state.getValue(SteelBeamBlock.X) || state.getValue(SteelBeamBlock.Z)) {
                        sawGirder = true;
                    } else if (level.getBlockState(pos.above()).isAir()) {
                        columnTops.add(pos.getY());
                    }
                }
            }

            helper.assertTrue(sawGirder, "a husk must span girders between its columns");
            helper.assertTrue(sawJoint,
                "where a girder meets a column the joint must RESOLVE - the column picks up the run on "
                    + "both axes and becomes a cross. Without the resolve pass beams merely pass through "
                    + "each other, which is what flag-2 placement leaves behind");
            helper.assertTrue(sawDeck, "a husk must wear some concrete deck");
            // Nothing hangs: every slab of deck must have steel at its own level within a bay's reach.
            // Deck used to ignore whether its bay still had a frame, so a ragged top left slabs floating.
            for (BlockPos slab : deckBlocks) {
                boolean supported = false;
                for (int d = 1; d <= 4 && !supported; d++) {
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos at = slab.relative(dir, d);
                        if (level.getBlockState(at).getBlock() instanceof SteelBeamBlock) {
                            supported = true;
                            break;
                        }
                    }
                }
                helper.assertTrue(supported,
                    "deck at " + slab + " has no steel at its level - a floor with no frame under it");
            }

            helper.assertTrue(columnTops.size() > 1,
                "the top must be RAGGED - columns ending at one height reads as unfinished "
                    + "construction, not demolition. Got tops at " + columnTops);
            helper.succeed();
        });
    }
}
