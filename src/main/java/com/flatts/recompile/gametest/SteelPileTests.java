package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.SteelBeamBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCFeatures;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * GameTests for the steel pile - the demolition yard's survival source of Steel I-Beams AND Reinforced
 * Concrete. The concrete half is load-bearing beyond this feature: concrete powder drops from reinforced
 * concrete, concrete is what the Cupola Furnace recipe takes, and the Cupola is the only iron machine. If
 * this feature stops placing concrete, iron quietly becomes unreachable in survival.
 */
final class SteelPileTests {

    private SteelPileTests() {
    }

    static void register() {
        RCGameTests.test("steel_pile_places_steel_and_concrete", 40, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));

            // Placed directly, the sortOnce convention: drive the feature rather than wait on worldgen.
            // Several attempts because the pile is deliberately sparse and randomised.
            Set<Block> found = new HashSet<>();
            for (int attempt = 0; attempt < 8; attempt++) {
                RandomSource random = RandomSource.create(attempt);
                RCFeatures.STEEL_PILE.get().place(new FeaturePlaceContext<>(
                    java.util.Optional.empty(), level, level.getChunkSource().getGenerator(),
                    random, origin, NoneFeatureConfiguration.INSTANCE));
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, 0, -4), origin.offset(4, 4, 4))) {
                    found.add(level.getBlockState(pos).getBlock());
                }
            }

            helper.assertTrue(found.contains(RCBlocks.STEEL_I_BEAM.get()),
                "a steel pile must place Steel I-Beams");
            helper.assertTrue(found.contains(RCBlocks.REINFORCED_CONCRETE.get()),
                "a steel pile must place Reinforced Concrete - it is the ONLY survival source, and the "
                    + "Cupola Furnace (and therefore all iron) depends on it");
            helper.succeed();
        });

        // The beams must land on MIXED axes. A heap of default-state beams would be neat upright columns,
        // which reads as a manufactured frame rather than a collapse.
        RCGameTests.test("steel_pile_beams_are_tangled", 40, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
            Set<Direction.Axis> axes = new HashSet<>();
            for (int attempt = 0; attempt < 12; attempt++) {
                RCFeatures.STEEL_PILE.get().place(new FeaturePlaceContext<>(
                    java.util.Optional.empty(), level, level.getChunkSource().getGenerator(),
                    RandomSource.create(attempt), origin, NoneFeatureConfiguration.INSTANCE));
                for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, 0, -4), origin.offset(4, 4, 4))) {
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof SteelBeamBlock) {
                        axes.add(state.getValue(SteelBeamBlock.AXIS));
                    }
                }
            }
            helper.assertTrue(axes.size() > 1,
                "a collapse must leave beams on more than one axis, got " + axes);
            helper.succeed();
        });
    }
}
