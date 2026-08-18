package com.flatts.recompile.gametest;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.LeachateBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCFeatures;
import com.flatts.recompile.registry.RCFluids;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
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

    private static boolean isPool(Holder<PlacedFeature> holder) {
        return holder.unwrapKey()
            .map(key -> key.identifier().equals(
                Identifier.fromNamespaceAndPath("recompile", "leachate_pool")))
            .orElse(false);
    }

    private static String name(Holder<PlacedFeature> holder) {
        return holder.unwrapKey().map(key -> key.identifier().toString()).orElse("<inline feature>");
    }

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
        // free clean-water source.
        //
        // THE RULE IS SCALE, NOT PURITY - the Dead Fridge sanctions exactly one water source (ice
        // from its teardown, owner ruling 2026-08-12) and the economy survives it, because that
        // costs a find, a prybar and a 1-in-4 draw. Leachate is AMBIENT: it is scattered across the
        // map in pools, so it answering yes would make every pool a tap, which is a different thing
        // entirely. Do not read the fridge exception as permission here.
        // IT DROWNS YOU, and that is a property of the fluid rather than of how deep anyone pours it.
        //
        // This value has now been set three times, which is worth recording rather than hiding. It
        // shipped true, described as unreachable "because pools are one block deep" - wrong, because
        // drowning is checked at the EYE and canSwim(true) is set, so a crawling or swimming player
        // already had eyes in a one-block body. It was set false to deliver a no-drowning guarantee that
        // depth could not. The owner then ruled the other way: the player should be able to drown in
        // leachate.
        //
        // Asserted on the FluidType so no generator has to remember it, and so the reversal cannot be
        // undone quietly by someone reading the old javadoc.
        RCGameTests.test("leachate_can_drown_you", 20, helper -> {
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            helper.assertTrue(RCFluids.LEACHATE_TYPE.get().canDrownIn(player),
                "leachate does not drown anyone - the owner's ruling is that it should, and depth cannot "
                    + "deliver that either way because the check is at eye level");
            helper.succeed();
        });

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

        // IT MAKES YOU ILL, AND THE CEILING MATTERS MORE THAN THE EFFECT (owner, 2026-08-05).
        //
        // Hunger, briefly, refreshed while you stand in it. The reason this is two assertions rather
        // than one is that "does it apply Hunger" is the easy half; the half worth defending is that
        // it applies NOTHING ELSE. A pond that quietly gained Poison or Wither later would still
        // pass a test that only looked for Hunger, and this mod's whole posture is that the world is
        // grim without being spiteful - encroachment never eats builds, mounds never bury you.
        RCGameTests.test("leachate_gives_hunger_and_nothing_worse", 40, helper -> {
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);   // instabuild is set by default and is exempt

            BlockPos abs = helper.absolutePos(GROUND);
            helper.setBlock(GROUND, RCBlocks.LEACHATE.get());
            player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);

            float healthBefore = player.getHealth();
            helper.assertTrue(LeachateBlock.sicken(helper.getLevel(), player),
                "a survival player standing in leachate must be affected");
            helper.succeedWhen(() -> {
                helper.assertTrue(player.hasEffect(MobEffects.HUNGER),
                    "standing in leachate must give Hunger");

                List<String> forbidden = new ArrayList<>();
                for (var held : player.getActiveEffects()) {
                    if (!held.getEffect().is(MobEffects.HUNGER)) {
                        forbidden.add(held.getEffect().getRegisteredName());
                    }
                }
                helper.assertTrue(forbidden.isEmpty(),
                    "leachate must apply Hunger and nothing else, also got: " + forbidden);
                helper.assertTrue(player.getHealth() >= healthBefore,
                    "leachate must never damage - health fell from " + healthBefore + " to "
                        + player.getHealth());
            });
        });

        // THE WIRING, which the test above is blind to. It calls sicken() directly, so the hook
        // could be missing entirely and it would still pass.
        //
        // This is not hypothetical here. The first implementation overrode Block.entityInside, which
        // compiled, read correctly, and is NEVER CALLED for a fluid - vanilla routes fluid effects
        // through the fluid path, and checkInsideBlocks is private besides. This test failed against
        // that version, which is the only reason it was caught before shipping. It now proves the
        // entity-tick hook in RCLeachateContact reaches a player who is simply standing in a pool.
        RCGameTests.test("standing_in_leachate_really_reaches_the_effect", 60, helper -> {
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);

            BlockPos abs = helper.absolutePos(GROUND);
            helper.setBlock(GROUND, RCBlocks.LEACHATE.get());
            player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);

            helper.succeedWhen(() -> helper.assertTrue(player.hasEffect(MobEffects.HUNGER),
                "standing in a pool must apply Hunger through the real hook - if this fails the "
                    + "effect is unreachable in a game no matter what sicken() does when called"));
        });

        // The switch works. "Everything ships config-gated, but defaults are the design" only holds
        // if a pack that wants harmless ponds can actually have them.
        RCGameTests.test("leachate_sickness_can_be_switched_off", 40, helper -> {
            var player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);

            BlockPos abs = helper.absolutePos(GROUND);
            helper.setBlock(GROUND, RCBlocks.LEACHATE.get());

            boolean was = RCConfig.LEACHATE_SICKENS.get();
            try {
                RCConfig.LEACHATE_SICKENS.set(false);
                player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
                helper.assertTrue(!LeachateBlock.sicken(helper.getLevel(), player),
                    "with leachateSickens off a pool must do nothing at all");
                helper.assertTrue(!player.hasEffect(MobEffects.HUNGER),
                    "with leachateSickens off no effect may be applied");
                helper.succeed();
            } finally {
                RCConfig.LEACHATE_SICKENS.set(was);
            }
        });

        // ORDERING, WHICH NO AMOUNT OF CARE INSIDE THE FEATURE CAN FIX (owner, 2026-08-05).
        //
        // A pool refuses any cell with something standing on it, but that check can only see blocks
        // that already exist. Pools originally sat in the LAKES step (features index 1), which runs
        // eight steps before the mounds in index 9 - so a pool under a mound footprint was placed
        // correctly, and then the mound was piled straight on top of it. In-world that reads as
        // leachate simply not being there, because it is under a mound, which is the one place a
        // player will never look.
        //
        // Asserted against the loaded biome registry rather than the JSON text, so a datapack that
        // overrides these biomes is held to the same rule.
        RCGameTests.test("leachate_pools_run_after_everything_that_piles_blocks", 20, helper -> {
            var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
            List<String> problems = new ArrayList<>();
            int checked = 0;

            for (var entry : biomes.entrySet()) {
                Identifier biomeId = entry.getKey().identifier();
                if (!biomeId.getNamespace().equals("recompile")) {
                    continue;
                }
                List<HolderSet<PlacedFeature>> steps = entry.getValue().getGenerationSettings().features();

                int poolStep = -1;
                int poolIndex = -1;
                for (int step = 0; step < steps.size(); step++) {
                    List<Holder<PlacedFeature>> inStep = steps.get(step).stream().toList();
                    for (int i = 0; i < inStep.size(); i++) {
                        if (isPool(inStep.get(i))) {
                            poolStep = step;
                            poolIndex = i;
                        }
                    }
                }
                if (poolStep < 0) {
                    continue; // biome has no pools; nothing to order
                }
                checked++;

                // Anything at all after the pool can bury it, so the rule is simply "nothing after".
                for (int step = 0; step < steps.size(); step++) {
                    List<Holder<PlacedFeature>> inStep = steps.get(step).stream().toList();
                    for (int i = 0; i < inStep.size(); i++) {
                        if (isPool(inStep.get(i))) {
                            continue;
                        }
                        if (step > poolStep || (step == poolStep && i > poolIndex)) {
                            problems.add(biomeId + " runs " + name(inStep.get(i))
                                + " at step " + step + " index " + i
                                + ", after leachate_pool at step " + poolStep + " index " + poolIndex);
                        }
                    }
                }
            }

            // Exactly one biome should carry pools: the household sprawl. The demolition yard was
            // dropped on purpose (owner, 2026-08-05) - leachate comes from refuse, and a yard full
            // of concrete and steel does not produce it.
            helper.assertTrue(checked == 1,
                "expected exactly one recompile biome to place leachate pools (the household "
                    + "sprawl), found " + checked + ". Zero means the feature was dropped from the "
                    + "biomes or this test reads the wrong registry; more than one means it came "
                    + "back somewhere it was deliberately removed from");
            helper.assertTrue(problems.isEmpty(),
                "leachate pools must run last, or whatever runs after them buries them: " + problems);
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
