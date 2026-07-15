package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

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

        // Regression: the mushroom has no BlockItem (it is foraged, not placed), so the
        // default getCloneItemStack returned an empty stack and pick-block silently did
        // nothing. Everything should pick-block.
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
    }
}
