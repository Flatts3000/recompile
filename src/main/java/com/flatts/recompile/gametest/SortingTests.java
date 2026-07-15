package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.GarbageBlock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;

/**
 * GameTests for the hand-sorting mechanic (design P0.4): pulling materials from a
 * garbage block and the block crumbling after 1-3 pulls.
 */
final class SortingTests {

    private SortingTests() {
    }

    static void register() {
        // Place a garbage block, pull the maximum number of times, and assert it
        // yielded material item entities and then crumbled to air.
        RCGameTests.test("garbage_block_sorts_then_crumbles", 60, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.GARBAGE_BLOCK.get());

            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);

            boolean crumbled = false;
            for (int i = 0; i < 3 && !crumbled; i++) {
                crumbled = GarbageBlock.sortOnce(level, abs);
            }

            helper.assertTrue(crumbled, "garbage block should crumble within 3 pulls");
            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.assertEntityPresent(EntityType.ITEM);
            helper.succeed();
        });
    }
}
