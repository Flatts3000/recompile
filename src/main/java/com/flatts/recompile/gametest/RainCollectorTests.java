package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.RainCollectorBlock;
import com.flatts.recompile.content.block.entity.RainCollectorBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

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
