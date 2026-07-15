package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;

/** GameTests for the forage tier (design P1.9): dump mushrooms on garbage mycelium. */
final class ForageTests {

    private ForageTests() {
    }

    static void register() {
        // Place mycelium, put a dump mushroom on it, and confirm it still stands a few
        // ticks later - its mayPlaceOn accepts garbage_mycelium in any light, so it
        // must not pop off the way a vanilla mushroom would in daylight.
        RCGameTests.test("dump_mushroom_survives_on_mycelium", 20, helper -> {
            BlockPos base = new BlockPos(1, 1, 1);
            helper.setBlock(base, RCBlocks.GARBAGE_MYCELIUM.get());
            helper.setBlock(base.above(), RCBlocks.DUMP_MUSHROOM.get());
            helper.runAfterDelay(2, () -> {
                helper.assertBlockPresent(RCBlocks.DUMP_MUSHROOM.get(), base.above());
                helper.succeed();
            });
        });
    }
}
