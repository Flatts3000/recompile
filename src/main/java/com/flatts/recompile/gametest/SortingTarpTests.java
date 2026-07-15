package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.SortingTarpBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

/** GameTests for the Sorting Tarp manual sift-into-world mechanic (design P1.3, revised 2026-07-14). */
final class SortingTarpTests {

    private SortingTarpTests() {
    }

    static void register() {
        // Place a tarp, sift one garbage block through it, and assert sorted material
        // item entities dropped into the world (stateless: no inventory, no GUI).
        RCGameTests.test("sorting_tarp_sifts_into_world", 60, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SORTING_TARP.get());

            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);

            SortingTarpBlock.siftInput(level, abs, RCItems.GARBAGE_BLOCK.get());

            helper.assertEntityPresent(EntityType.ITEM);
            helper.succeed();
        });
    }
}
