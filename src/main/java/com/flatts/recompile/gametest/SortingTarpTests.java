package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.SortingTarpBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

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

        // #68: the yard's material sifts too. Asserting the drops are SHARDS is the point - the tarp
        // routes a pull table per input, so sifting rubble against household_pulls would still drop
        // items and still pass an "something dropped" check while handing out the wrong economy.
        RCGameTests.test("sorting_tarp_sifts_stone_rubble", 60, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SORTING_TARP.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);

            SortingTarpBlock.siftInput(level, abs, RCItems.STONE_RUBBLE.get());

            List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(4));
            helper.assertTrue(!dropped.isEmpty(), "sifting rubble must drop something");
            for (ItemEntity e : dropped) {
                helper.assertTrue(e.getItem().is(RCTags.STONE_SHARDS),
                    "rubble must sift into stone shards, got " + e.getItem().getItem());
            }
            helper.succeed();
        });
    }
}
