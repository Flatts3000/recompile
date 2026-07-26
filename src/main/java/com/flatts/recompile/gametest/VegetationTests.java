package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * GameTests for the Vegetation tier (rung 2). Phase 1 covers the custom dump plants as blocks; the
 * Fertilizer scatter and the frontier_cover hookup arrive with Phase 2.
 */
final class VegetationTests {

    private VegetationTests() {
    }

    static void register() {
        // A dump plant survives on grass and dirt (normal plantable ground), not on non-plantable
        // blocks. The reclaimed-only rule lives at Fertilizer's placement gate, not here.
        RCGameTests.test("dump_plant_survives_on_plantable_ground", 20, helper -> {
            BlockPos ground = new BlockPos(1, 1, 1);
            BlockPos plant = ground.above();
            BlockState weedgrass = RCBlocks.WEEDGRASS.get().defaultBlockState();

            helper.setBlock(ground, Blocks.GRASS_BLOCK);
            helper.assertTrue(weedgrass.canSurvive(helper.getLevel(), helper.absolutePos(plant)),
                "weedgrass must survive on grass");

            helper.setBlock(ground, Blocks.COARSE_DIRT);
            helper.assertTrue(weedgrass.canSurvive(helper.getLevel(), helper.absolutePos(plant)),
                "weedgrass must survive on coarse dirt");

            helper.setBlock(ground, Blocks.STONE);
            helper.assertFalse(weedgrass.canSurvive(helper.getLevel(), helper.absolutePos(plant)),
                "weedgrass must not survive on stone");

            helper.succeed();
        });
    }
}
