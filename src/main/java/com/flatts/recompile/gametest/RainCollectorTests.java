package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.RainCollectorBlock;
import com.flatts.recompile.content.block.entity.RainCollectorBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** GameTests for the Rain Collector (design P1.10): the two-cell structure and the water tank. */
final class RainCollectorTests {

    private RainCollectorTests() {
    }

    static void register() {
        // Two cells like a door; breaking one takes the other with it (updateShape, not a hook).
        RCGameTests.test("rain_collector_places_and_breaks_as_two_halves", 20, helper -> {
            BlockPos lower = new BlockPos(1, 1, 1);
            helper.setBlock(lower, RCBlocks.RAIN_COLLECTOR.get().defaultBlockState()
                .setValue(RainCollectorBlock.HALF, DoubleBlockHalf.LOWER));
            helper.setBlock(lower.above(), RCBlocks.RAIN_COLLECTOR.get().defaultBlockState()
                .setValue(RainCollectorBlock.HALF, DoubleBlockHalf.UPPER));
            helper.assertBlockPresent(RCBlocks.RAIN_COLLECTOR.get(), lower);
            helper.assertBlockPresent(RCBlocks.RAIN_COLLECTOR.get(), lower.above());

            helper.setBlock(lower, Blocks.AIR);
            helper.assertBlockPresent(Blocks.AIR, lower.above());
            helper.succeed();
        });

        // The tank fills from rain, and a glass bottle draws a water bottle - the whole point.
        // Rain is driven directly (catchRain) since a GameTest can't summon weather.
        RCGameTests.test("rain_collector_catches_rain_then_fills_a_bottle", 40, helper -> {
            BlockPos lower = new BlockPos(1, 1, 1);
            helper.setBlock(lower, RCBlocks.RAIN_COLLECTOR.get());  // default LOWER - holds the tank
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(lower))
                    instanceof RainCollectorBlockEntity be)) {
                helper.fail("rain collector base has no BlockEntity");
                return;
            }
            be.catchRain();
            be.catchRain();
            be.catchRain();
            helper.assertTrue(be.storedWater() >= 250,
                "rain should accumulate water, got " + be.storedWater() + " mB");

            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            Vec3 standing = helper.absoluteVec(lower.above().above().getCenter());
            player.snapTo(standing.x, standing.y, standing.z);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));

            int before = be.storedWater();
            helper.useBlock(lower, player);

            helper.assertTrue(hasItem(player, Items.POTION),
                "a glass bottle at a filled collector must yield a water bottle");
            helper.assertTrue(be.storedWater() == before - 250,
                "filling a bottle must drain 250 mB, was " + before + " now " + be.storedWater());
            helper.succeed();
        });

        // Breaking a collector must return exactly one item, from either half - the loot is
        // gated to part=lower, and breaking either half rolls loot twice (the broken half plus
        // the orphan updateShape destroys), so the gate is the only thing filtering to one.
        RCGameTests.test("rain_collector_broken_drops_one", 40, helper -> {
            BlockPos lower = new BlockPos(1, 1, 1);
            helper.setBlock(lower, RCBlocks.RAIN_COLLECTOR.get().defaultBlockState()
                .setValue(RainCollectorBlock.HALF, DoubleBlockHalf.LOWER));
            helper.setBlock(lower.above(), RCBlocks.RAIN_COLLECTOR.get().defaultBlockState()
                .setValue(RainCollectorBlock.HALF, DoubleBlockHalf.UPPER));
            helper.getLevel().destroyBlock(helper.absolutePos(lower), true);
            helper.assertBlockPresent(Blocks.AIR, lower);
            helper.assertBlockPresent(Blocks.AIR, lower.above());
            helper.succeedWhen(() ->
                helper.assertItemEntityCountIs(RCItems.RAIN_COLLECTOR.get(), lower, 3.0, 1));
        });

        // Water-only: a pipe (or anything) pushing a non-water fluid must be rejected by the tank.
        RCGameTests.test("rain_collector_rejects_non_water", 20, helper -> {
            BlockPos lower = new BlockPos(1, 1, 1);
            helper.setBlock(lower, RCBlocks.RAIN_COLLECTOR.get());
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(lower))
                    instanceof RainCollectorBlockEntity be)) {
                helper.fail("rain collector base has no BlockEntity");
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

        // The whole point: it must actually collect rain. Turn on the weather, place it under
        // open sky, tick, and assert the tank rises - the regression guard for the bug where
        // it relied on the far-too-rare handlePrecipitation instead of a ticker.
        RCGameTests.test("rain_collector_fills_while_raining", 140, helper -> {
            net.minecraft.world.level.saveddata.WeatherData weather = helper.getLevel().getWeatherData();
            weather.setRaining(true);   // isRaining() reads a level that ramps up over ~20 ticks
            weather.setRainTime(100000);
            BlockPos lower = new BlockPos(1, 1, 1);
            helper.setBlock(lower, RCBlocks.RAIN_COLLECTOR.get().defaultBlockState()
                .setValue(RainCollectorBlock.HALF, DoubleBlockHalf.LOWER));
            helper.setBlock(lower.above(), RCBlocks.RAIN_COLLECTOR.get().defaultBlockState()
                .setValue(RainCollectorBlock.HALF, DoubleBlockHalf.UPPER));
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(lower))
                    instanceof RainCollectorBlockEntity be)) {
                helper.fail("rain collector base has no BlockEntity");
                return;
            }
            helper.assertTrue(be.storedWater() == 0, "a fresh collector starts empty");
            helper.runAfterDelay(60, () -> {
                helper.assertTrue(be.storedWater() > 0,
                    "a collector under open sky must fill while raining, got " + be.storedWater() + " mB");
                helper.succeed();
            });
        });

        // A dry collector refuses: no water bottle, and the glass bottle is not eaten.
        RCGameTests.test("rain_collector_dry_refuses_a_bottle", 20, helper -> {
            BlockPos lower = new BlockPos(1, 1, 1);
            helper.setBlock(lower, RCBlocks.RAIN_COLLECTOR.get());
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            Vec3 standing = helper.absoluteVec(lower.above().above().getCenter());
            player.snapTo(standing.x, standing.y, standing.z);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));

            helper.useBlock(lower, player);

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
            BlockPos lower = new BlockPos(1, 1, 1);
            helper.setBlock(lower, RCBlocks.RAIN_COLLECTOR.get());
            var registries = helper.getLevel().registryAccess();
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(lower))
                    instanceof RainCollectorBlockEntity be)) {
                helper.fail("rain collector base has no BlockEntity");
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
