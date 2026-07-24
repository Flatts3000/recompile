package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.RainCollectorBlockEntity;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCDataComponents;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * GameTests for the Rain Collector (design P1.10), now a two-cell multiblock: a core that holds the
 * tank plus a Machine Frame that forms into the tarp funnel.
 *
 * <p>Two groups here. The <b>structure</b> tests cover the multiblock framework itself - forming,
 * refusing to form, and disbanding without duping or eating items - and are the first proof of it,
 * so the grass spreader inherits tested machinery. The <b>tank</b> tests are the P1.10 behaviour
 * carried over unchanged; they exist to prove the restructure did not regress the water.
 */
final class RainCollectorTests {

    /** The core sits here; the frame/funnel cell is directly above. */
    private static final BlockPos CORE = new BlockPos(1, 1, 1);

    private RainCollectorTests() {
    }

    /** Build a formed collector the way the game does: core, frame on top, form. */
    private static void formCollector(net.minecraft.gametest.framework.GameTestHelper helper) {
        helper.setBlock(CORE, RCBlocks.RAIN_COLLECTOR.get());
        helper.setBlock(CORE.above(), RCBlocks.RAIN_COLLECTOR_FUNNEL.get());
        MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(CORE));
    }

    static void register() {
        registerStructure();
        registerTank();
    }

    // ---------------- the multiblock framework ----------------

    private static void registerStructure() {
        // A core alone is inert. If this ever auto-formed, the frame would stop being a real cost.
        RCGameTests.test("rain_collector_core_alone_is_unformed", 20, helper -> {
            helper.setBlock(CORE, RCBlocks.RAIN_COLLECTOR.get());

            helper.assertTrue(!MultiblockCoreBlock.isFormed(helper.getBlockState(CORE)),
                "a core with nothing on top must be unformed");
            helper.assertBlockPresent(Blocks.AIR, CORE.above());
            helper.succeed();
        });

        // Stacking the component by hand forms the machine, and the frame becomes the funnel -
        // the "loose parts in, machine out" beat the whole system exists for.
        RCGameTests.test("rain_collector_frame_on_top_forms_it", 20, helper -> {
            formCollector(helper);

            helper.assertTrue(MultiblockCoreBlock.isFormed(helper.getBlockState(CORE)),
                "a core with a frame above must form");
            helper.assertBlockPresent(RCBlocks.RAIN_COLLECTOR_FUNNEL.get(), CORE.above());
            helper.succeed();
        });

        // The blueprint is an allowlist: a wrong block above is not a substitute for the frame.
        RCGameTests.test("rain_collector_wrong_block_does_not_form", 20, helper -> {
            helper.setBlock(CORE, RCBlocks.RAIN_COLLECTOR.get());
            helper.setBlock(CORE.above(), RCBlocks.PRESSED_JUNK_BLOCK.get());
            MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(CORE));

            helper.assertTrue(!MultiblockCoreBlock.isFormed(helper.getBlockState(CORE)),
                "only the blueprint's component may form the machine");
            helper.succeed();
        });

        // Breaking the core takes the funnel with it and returns exactly one of each part.
        // Duping or eating a component here would be invisible in play until someone counted.
        RCGameTests.test("rain_collector_breaking_core_disbands_once", 40, helper -> {
            formCollector(helper);
            helper.getLevel().destroyBlock(helper.absolutePos(CORE), true);

            helper.assertBlockPresent(Blocks.AIR, CORE);
            helper.assertBlockPresent(Blocks.AIR, CORE.above());
            helper.succeedWhen(() -> {
                helper.assertItemEntityCountIs(RCItems.RAIN_COLLECTOR.get(), CORE, 3.0, 1);
                helper.assertItemEntityCountIs(RCItems.RAIN_COLLECTOR_FUNNEL.get(), CORE, 3.0, 1);
            });
        });

        // Breaking a FILLED machine must hand the water back. The component plumbing is unit-tested
        // below, and the drops are counted above, but neither covers the actual player path - and
        // the funnel path is the risky one, because the core's loot is rolled by hand there and
        // passing the wrong BlockEntity would silently drop an empty collector with every test
        // still green. Assert the dropped item really carries the water.
        RCGameTests.test("rain_collector_break_returns_the_water", 40, helper -> {
            formCollector(helper);
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CORE))
                    instanceof RainCollectorBlockEntity be)) {
                helper.fail("rain collector core has no BlockEntity");
                return;
            }
            be.catchRain();
            be.catchRain();
            int stored = be.storedWater();
            helper.assertTrue(stored > 0, "precondition: the tank was filled");

            // break from the TOP, the hand-rolled path
            helper.getLevel().destroyBlock(helper.absolutePos(CORE.above()), true);

            helper.succeedWhen(() -> {
                ItemStack dropped = null;
                for (var entity : helper.getLevel().getEntities(
                        net.minecraft.world.entity.EntityType.ITEM,
                        net.minecraft.world.phys.AABB.encapsulatingFullBlocks(
                            helper.absolutePos(CORE).offset(-3, -3, -3),
                            helper.absolutePos(CORE).offset(3, 3, 3)),
                        e -> e.getItem().is(RCItems.RAIN_COLLECTOR.get()))) {
                    dropped = entity.getItem();
                }
                helper.assertTrue(dropped != null, "breaking the funnel must drop the collector");
                Integer carried = dropped.get(RCDataComponents.RAIN_WATER.get());
                helper.assertTrue(carried != null && carried == stored,
                    "the dropped collector must carry its " + stored + " mB, got " + carried);
            });
        });

        // ...and the same from the other end. This is the case that recurses if the two removal
        // handlers ever call destroyBlock on each other instead of dropResources + setBlock.
        RCGameTests.test("rain_collector_breaking_funnel_disbands_once", 40, helper -> {
            formCollector(helper);
            helper.getLevel().destroyBlock(helper.absolutePos(CORE.above()), true);

            helper.assertBlockPresent(Blocks.AIR, CORE);
            helper.assertBlockPresent(Blocks.AIR, CORE.above());
            helper.succeedWhen(() -> {
                helper.assertItemEntityCountIs(RCItems.RAIN_COLLECTOR.get(), CORE, 3.0, 1);
                helper.assertItemEntityCountIs(RCItems.RAIN_COLLECTOR_FUNNEL.get(), CORE, 3.0, 1);
            });
        });
    }

    // ---------------- the tank (P1.10 behaviour, must not regress) ----------------

    private static void registerTank() {
        // The tank fills from rain, and a glass bottle draws a water bottle - the whole point.
        // Rain is driven directly (catchRain) since a GameTest can't summon weather.
        RCGameTests.test("rain_collector_catches_rain_then_fills_a_bottle", 40, helper -> {
            formCollector(helper);
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CORE))
                    instanceof RainCollectorBlockEntity be)) {
                helper.fail("rain collector core has no BlockEntity");
                return;
            }
            be.catchRain();
            be.catchRain();
            be.catchRain();
            helper.assertTrue(be.storedWater() >= 250,
                "rain should accumulate water, got " + be.storedWater() + " mB");

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            Vec3 standing = helper.absoluteVec(CORE.above().above().getCenter());
            player.snapTo(standing.x, standing.y, standing.z);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));

            int before = be.storedWater();
            helper.useBlock(CORE, player);

            helper.assertTrue(hasItem(player, Items.POTION),
                "a glass bottle at a filled collector must yield a water bottle");
            helper.assertTrue(be.storedWater() == before - 250,
                "filling a bottle must drain 250 mB");
            helper.succeed();
        });

        // Water-only: a pipe (or anything) pushing a non-water fluid must be rejected by the tank.
        RCGameTests.test("rain_collector_rejects_non_water", 20, helper -> {
            helper.setBlock(CORE, RCBlocks.RAIN_COLLECTOR.get());
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CORE))
                    instanceof RainCollectorBlockEntity be)) {
                helper.fail("rain collector core has no BlockEntity");
                return;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = be.fluidHandler().insert(FluidResource.of(Fluids.LAVA), 1000, transaction);
                helper.assertTrue(inserted == 0, "the water tank must reject lava, inserted " + inserted);
                transaction.commit();
            }
            helper.assertTrue(be.storedWater() == 0, "a rejected fluid must not be stored");
            helper.succeed();
        });

        // It must actually collect rain - the regression guard for the bug where it relied on the
        // far-too-rare handlePrecipitation instead of a ticker. Now also proves the ticker runs
        // when FORMED, since that is what gates it.
        RCGameTests.test("rain_collector_fills_while_raining", 140, helper -> {
            net.minecraft.world.level.saveddata.WeatherData weather = helper.getLevel().getWeatherData();
            weather.setRaining(true);   // isRaining() reads a level that ramps up over ~20 ticks
            weather.setRainTime(100000);
            formCollector(helper);
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CORE))
                    instanceof RainCollectorBlockEntity be)) {
                helper.fail("rain collector core has no BlockEntity");
                return;
            }
            helper.assertTrue(be.storedWater() == 0, "a fresh collector starts empty");
            helper.runAfterDelay(60, () -> {
                helper.assertTrue(be.storedWater() > 0,
                    "a formed collector under open sky must fill while raining, got "
                        + be.storedWater() + " mB");
                helper.succeed();
            });
        });

        // The funnel is what catches the rain, so an unformed core must collect nothing. Without
        // this the unformed state would be purely cosmetic and the frame would be optional.
        RCGameTests.test("rain_collector_unformed_collects_nothing", 100, helper -> {
            net.minecraft.world.level.saveddata.WeatherData weather = helper.getLevel().getWeatherData();
            weather.setRaining(true);
            weather.setRainTime(100000);
            helper.setBlock(CORE, RCBlocks.RAIN_COLLECTOR.get());   // no frame: unformed
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CORE))
                    instanceof RainCollectorBlockEntity be)) {
                helper.fail("rain collector core has no BlockEntity");
                return;
            }
            helper.runAfterDelay(60, () -> {
                helper.assertTrue(be.storedWater() == 0,
                    "an unformed collector must not catch rain, got " + be.storedWater() + " mB");
                helper.succeed();
            });
        });

        // A dry collector refuses: no water bottle, and the glass bottle is not eaten.
        RCGameTests.test("rain_collector_dry_refuses_a_bottle", 20, helper -> {
            formCollector(helper);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            Vec3 standing = helper.absoluteVec(CORE.above().above().getCenter());
            player.snapTo(standing.x, standing.y, standing.z);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));

            helper.useBlock(CORE, player);

            helper.assertFalse(hasItem(player, Items.POTION),
                "a dry collector must not produce a water bottle");
            helper.assertTrue(hasItem(player, Items.GLASS_BOTTLE),
                "a dry collector must not consume the glass bottle");
            helper.succeed();
        });

        // The tank's water must survive a save/load - the one behaviour the other tests can't
        // reach (they never serialize). A wrong child()/serialize pairing would silently drop
        // the water on world reload.
        RCGameTests.test("rain_collector_tank_survives_reload", 20, helper -> {
            helper.setBlock(CORE, RCBlocks.RAIN_COLLECTOR.get());
            var registries = helper.getLevel().registryAccess();
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CORE))
                    instanceof RainCollectorBlockEntity be)) {
                helper.fail("rain collector core has no BlockEntity");
                return;
            }
            be.catchRain();
            be.catchRain();
            int stored = be.storedWater();
            helper.assertTrue(stored > 0, "precondition: the tank was filled");

            net.minecraft.nbt.CompoundTag tag = be.saveCustomOnly(registries);
            RainCollectorBlockEntity reloaded =
                new RainCollectorBlockEntity(be.getBlockPos(), be.getBlockState());
            reloaded.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, registries, tag));

            helper.assertTrue(reloaded.storedWater() == stored,
                "the tank must survive save/load; saved " + stored + " mB, reloaded "
                    + reloaded.storedWater() + " mB");
            helper.succeed();
        });

        // The water must survive break + replace, not just save/load: the broken collector's item
        // carries the water (the rain_water component the loot table copies off the BlockEntity),
        // and a freshly placed collector refills from it. Reproduces the "loses the water" bug.
        RCGameTests.test("rain_collector_water_survives_break_replace", 20, helper -> {
            helper.setBlock(CORE, RCBlocks.RAIN_COLLECTOR.get());
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(CORE))
                    instanceof RainCollectorBlockEntity be)) {
                helper.fail("rain collector core has no BlockEntity");
                return;
            }
            be.catchRain();
            be.catchRain();
            int stored = be.storedWater();
            helper.assertTrue(stored > 0, "precondition: the tank was filled");

            // Break: the dropped item carries the water as an item component.
            var components = be.collectComponents();
            Integer carried = components.get(RCDataComponents.RAIN_WATER.get());
            helper.assertTrue(carried != null && carried == stored,
                "a broken collector must carry its water, got " + carried);

            // Replace: a fresh collector applies the component and refills to the same level.
            RainCollectorBlockEntity replaced =
                new RainCollectorBlockEntity(be.getBlockPos(), be.getBlockState());
            replaced.applyComponents(components, net.minecraft.core.component.DataComponentPatch.EMPTY);
            helper.assertTrue(replaced.storedWater() == stored,
                "a replaced collector must restore its water; expected " + stored
                    + " got " + replaced.storedWater());
            helper.succeed();
        });
    }

    private static boolean hasItem(Player player, net.minecraft.world.item.Item item) {
        var inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            if (inv.getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }
}
