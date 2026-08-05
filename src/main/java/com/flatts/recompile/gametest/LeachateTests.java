package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCFeatures;
import com.flatts.recompile.registry.RCFluids;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Leachate (#156, design I-8): the liquid a landfill actually produces.
 *
 * <p>The interesting assertions here are all <b>negative</b> ones. A fluid that places, flows and
 * renders correctly but quietly waters the farm next to it would pass every structural check in this
 * repo and silently disable a shipped mechanic, so the tests that earn their place are the ones that
 * pin down what leachate must <i>not</i> do.
 */
final class LeachateTests {

    private LeachateTests() {
    }

    private static final BlockPos GROUND = new BlockPos(1, 1, 1);

    /** Same idiom as {@code SteelStackTests}: drive the registered feature directly at a position. */
    private static boolean placePool(ServerLevel level, BlockPos origin) {
        return RCFeatures.LEACHATE_POOL.get().place(new FeaturePlaceContext<>(
            Optional.empty(), level, level.getChunkSource().getGenerator(),
            RandomSource.create(7), origin, NoneFeatureConfiguration.INSTANCE));
    }

    static void register() {
        // THE DECISION THIS WHOLE FEATURE HANGS ON (owner, 2026-08-05). RCEncroachment's one
        // blockstate rule is "wet farmland holds, dry farmland is taken", and vanilla farmland
        // hydrates from anything within four blocks. So a leachate pool that irrigates is permanent,
        // free encroachment immunity for every plot in range - and encroachment defence is supposed
        // to be something the player builds and maintains.
        //
        // Asserted through the real farmland block rather than by reading FluidType.canHydrate,
        // because the property is only one of two paths in (FarmlandWaterManager hands out water
        // tickets too) and a test that reads the flag would pass while the block stayed wet.
        RCGameTests.test("leachate_does_not_water_farmland", 60, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos farm = GROUND.east();

            helper.setBlock(GROUND, RCBlocks.LEACHATE.get());
            helper.setBlock(farm, Blocks.FARMLAND);

            BlockPos absFarm = helper.absolutePos(farm);
            // Farmland only re-evaluates moisture on a random tick, so drive a run of them rather
            // than waiting: one tick proves nothing, and a fixed delay would be a race.
            //
            // Two outcomes both mean "never got wet", and the test accepts either. Moisture that
            // stays at 0 is the obvious one. Reverting to dirt is the other: unwatered farmland
            // decays and vanilla turns it back to dirt, which is exactly the fate a plot beside a
            // leachate pool is supposed to suffer. An earlier version of this test asserted the
            // block was still farmland and failed on that second, correct outcome.
            for (int i = 0; i < 40; i++) {
                BlockState state = level.getBlockState(absFarm);
                if (!state.is(Blocks.FARMLAND)) {
                    break; // dried out and reverted - proof enough, and nothing left to tick
                }
                int moisture = state.getValue(FarmlandBlock.MOISTURE);
                helper.assertTrue(moisture == 0,
                    "leachate must never irrigate - farmland beside a pool reached moisture "
                        + moisture + " on tick " + i + ", which would make every plot within four "
                        + "blocks permanently immune to encroachment for free");
                state.randomTick(level, absFarm, level.getRandom());
            }

            helper.succeed();
        });

        // The control for the test above. Without it, "moisture stayed 0" could equally mean the
        // test never drove a working hydration path at all, and both halves would pass forever
        // against a farmland block that simply never wets.
        RCGameTests.test("but_water_does_water_farmland", 60, helper -> {
            ServerLevel level = helper.getLevel();
            BlockPos farm = GROUND.east();

            helper.setBlock(GROUND, Blocks.WATER);
            helper.setBlock(farm, Blocks.FARMLAND);

            BlockPos absFarm = helper.absolutePos(farm);
            helper.succeedWhen(() -> {
                BlockState state = level.getBlockState(absFarm);
                state.randomTick(level, absFarm, level.getRandom());
                helper.assertTrue(state.getValue(FarmlandBlock.MOISTURE) > 0,
                    "water must still irrigate - if this fails the no-irrigation test above is "
                        + "passing vacuously and proves nothing about leachate");
            });
        });

        // It is not water, and the distinction is load-bearing rather than cosmetic: the Rain
        // Collector's tank accepts water only, so anything that answers "yes" to being water is a
        // free clean-water source and the P1.10 water economy stops meaning anything.
        RCGameTests.test("leachate_is_not_water", 20, helper -> {
            helper.setBlock(GROUND, RCBlocks.LEACHATE.get());
            BlockPos abs = helper.absolutePos(GROUND);

            var fluid = helper.getLevel().getFluidState(abs);
            helper.assertTrue(fluid.getType() == RCFluids.LEACHATE.get(),
                "the leachate block must carry the leachate fluid, got " + fluid.getType());
            helper.assertTrue(!fluid.is(Fluids.WATER) && !fluid.is(Fluids.FLOWING_WATER),
                "leachate must not be water - the Rain Collector accepts water and only water, so "
                    + "this would turn every pool into a clean-water tap");
            helper.succeed();
        });

        // A pool must actually appear, and it must be a basin rather than a slab dropped on the
        // surface: an unsupported source block flows until it finds an edge, so "did it place" and
        // "did it stay put" are the same question asked twice.
        RCGameTests.test("a_leachate_pool_digs_itself_into_the_ground", 40, helper -> {
            ServerLevel level = helper.getLevel();
            // A patch of ground to pool in, with solid rock beneath so the floor check passes.
            for (int dx = 0; dx < 5; dx++) {
                for (int dz = 0; dz < 5; dz++) {
                    helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
                    helper.setBlock(new BlockPos(dx, 1, dz), Blocks.COARSE_DIRT);
                }
            }

            BlockPos centre = new BlockPos(2, 1, 2);
            boolean placed = placePool(level, helper.absolutePos(centre));
            helper.assertTrue(placed,
                "the feature must find a home on plain coarse dirt with rock under it");

            helper.assertTrue(
                level.getBlockState(helper.absolutePos(centre)).is(RCBlocks.LEACHATE.get()),
                "the centre of the pool must be leachate - if the feature reports success without "
                    + "filling its own origin, it has placed a pool somewhere nobody asked for");
            helper.succeed();
        });

        // Pools eat dirt, never a mound. MoundFeature's memory lives UNDER the footprint, so a pool
        // that carved into garbage would hollow out a mound from the side and leave what regrows
        // falling into a pond.
        RCGameTests.test("a_leachate_pool_will_not_eat_a_mound", 40, helper -> {
            ServerLevel level = helper.getLevel();
            for (int dx = 0; dx < 5; dx++) {
                for (int dz = 0; dz < 5; dz++) {
                    helper.setBlock(new BlockPos(dx, 0, dz), Blocks.STONE);
                    helper.setBlock(new BlockPos(dx, 1, dz), RCBlocks.GARBAGE_BLOCK.get());
                }
            }

            BlockPos centre = new BlockPos(2, 1, 2);
            placePool(level, helper.absolutePos(centre));

            for (int dx = 0; dx < 5; dx++) {
                for (int dz = 0; dz < 5; dz++) {
                    BlockPos abs = helper.absolutePos(new BlockPos(dx, 1, dz));
                    helper.assertTrue(!level.getBlockState(abs).is(RCBlocks.LEACHATE.get()),
                        "a pool must never replace garbage - it would hollow a mound out sideways");
                }
            }
            helper.succeed();
        });
    }
}
