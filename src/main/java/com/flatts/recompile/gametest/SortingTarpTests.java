package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.SortingTarpBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** GameTests for the Sorting Tarp auto-sort (design P1.3). */
final class SortingTarpTests {

    private SortingTarpTests() {
    }

    static void register() {
        // Place a tarp, drop a garbage block in the input, tick it through one full
        // process cycle, and assert the input was consumed and materials came out.
        RCGameTests.test("sorting_tarp_sorts_input", 60, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SORTING_TARP.get());

            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);
            if (!(level.getBlockEntity(abs) instanceof SortingTarpBlockEntity tarp)) {
                helper.fail("sorting tarp has no block entity");
                return;
            }

            tarp.getInventory().setStackInSlot(SortingTarpBlockEntity.INPUT_SLOT,
                new ItemStack(RCItems.GARBAGE_BLOCK.get()));

            for (int i = 0; i <= SortingTarpBlockEntity.PROCESS_TICKS; i++) {
                SortingTarpBlockEntity.serverTick(level, abs, level.getBlockState(abs), tarp);
            }

            helper.assertTrue(tarp.getInventory().getStackInSlot(SortingTarpBlockEntity.INPUT_SLOT).isEmpty(),
                "tarp should have consumed the input block");

            boolean producedMaterials = false;
            for (int slot = SortingTarpBlockEntity.OUTPUT_START; slot < SortingTarpBlockEntity.SLOT_COUNT; slot++) {
                if (!tarp.getInventory().getStackInSlot(slot).isEmpty()) {
                    producedMaterials = true;
                    break;
                }
            }
            helper.assertTrue(producedMaterials, "tarp should have produced sorted materials");
            helper.succeed();
        });
    }
}
