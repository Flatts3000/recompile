package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** GameTests for the forage tier (design P1.9): dump mushrooms on garbage mycelium. */
final class ForageTests {

    private ForageTests() {
    }

    static void register() {
        // Place mycelium, put a dump mushroom on it, and confirm it still stands a few
        // ticks later - its mayPlaceOn accepts vanilla mycelium in any light, so it
        // must not pop off the way a vanilla mushroom would in daylight.
        RCGameTests.test("dump_mushroom_survives_on_mycelium", 20, helper -> {
            BlockPos base = new BlockPos(1, 1, 1);
            helper.setBlock(base, Blocks.MYCELIUM);
            helper.setBlock(base.above(), RCBlocks.DUMP_MUSHROOM.get());
            helper.runAfterDelay(2, () -> {
                helper.assertBlockPresent(RCBlocks.DUMP_MUSHROOM.get(), base.above());
                helper.succeed();
            });
        });

        // Pick-block yields the edible mushroom item. The item is now the block's BlockItem, so the
        // default getCloneItemStack returns it - no override needed.
        RCGameTests.test("dump_mushroom_pick_block_yields_item", 20, helper -> {
            BlockPos base = new BlockPos(1, 1, 1);
            helper.setBlock(base, Blocks.MYCELIUM);
            helper.setBlock(base.above(), RCBlocks.DUMP_MUSHROOM.get());

            BlockPos abs = helper.absolutePos(base.above());
            ItemStack picked = helper.getLevel().getBlockState(abs)
                .getCloneItemStack(helper.getLevel(), abs, false);

            helper.assertFalse(picked.isEmpty(), "pick-block on a dump mushroom must yield an item");
            helper.assertTrue(picked.is(RCItems.DUMP_MUSHROOM.get()),
                "pick-block must yield the edible dump mushroom, got " + picked);
            helper.succeed();
        });

        // Parity with vanilla mushrooms: the item is a BlockItem, so a foraged mushroom replants on
        // mycelium. This is what makes foraging renewable rather than a one-way strip of the world.
        RCGameTests.test("dump_mushroom_item_replants_on_mycelium", 20, helper -> {
            BlockPos ground = new BlockPos(1, 1, 1);
            helper.setBlock(ground, Blocks.MYCELIUM);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RCItems.DUMP_MUSHROOM.get()));

            BlockPos groundAbs = helper.absolutePos(ground);
            RCItems.DUMP_MUSHROOM.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(groundAbs), Direction.UP, groundAbs, false)));

            helper.assertBlockPresent(RCBlocks.DUMP_MUSHROOM.get(), ground.above());
            helper.succeed();
        });
    }
}
