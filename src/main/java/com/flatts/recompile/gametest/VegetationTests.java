package com.flatts.recompile.gametest;

import com.flatts.recompile.event.FertilizerScatter;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests for the Vegetation tier (rung 2): the dump plants as blocks, and Fertilizer's surface-aware
 * scatter (grass -> weeds/flowers, mycelium -> mushrooms) with its grass/mycelium gate and the
 * frontier_cover hookup.
 */
final class VegetationTests {

    private VegetationTests() {
    }

    static void register() {
        // A dump plant survives on grass and dirt (normal plantable ground), not on non-plantable blocks.
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

        // The scatter drops plants on grass and mushrooms on mycelium (bonemeal footprint, placed here
        // immediately via the test seam rather than over the ripple).
        RCGameTests.test("fertilizer_scatters_on_grass_and_mycelium", 20, helper -> {
            ServerLevel level = helper.getLevel();
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
                }
            }
            int onGrass = FertilizerScatter.scatterForTest(level, helper.absolutePos(new BlockPos(2, 1, 2)), true);
            helper.assertTrue(onGrass >= 1, "fertilizer must scatter plants on grass, placed " + onGrass);

            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    helper.setBlock(new BlockPos(x, 3, z), Blocks.MYCELIUM);
                }
            }
            int onMyc = FertilizerScatter.scatterForTest(level, helper.absolutePos(new BlockPos(2, 3, 2)), false);
            helper.assertTrue(onMyc >= 1, "fertilizer must scatter mushrooms on mycelium, placed " + onMyc);
            helper.succeed();
        });

        // Grass/mycelium gate: fertilizing stone is a no-op and consumes nothing.
        RCGameTests.test("fertilizer_gates_on_grass_or_mycelium", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ItemStack fert = new ItemStack(RCItems.FERTILIZER.get(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, fert);

            BlockPos stone = new BlockPos(1, 1, 1);
            helper.setBlock(stone, Blocks.STONE);
            BlockPos stoneAbs = helper.absolutePos(stone);
            InteractionResult onStone = RCItems.FERTILIZER.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(stoneAbs), Direction.UP, stoneAbs, false)));
            helper.assertTrue(onStone == InteractionResult.PASS, "fertilizer on stone must PASS, got " + onStone);
            helper.assertTrue(fert.getCount() == 3, "fertilizer on stone must not be consumed, count=" + fert.getCount());

            BlockPos grass = new BlockPos(1, 1, 3);
            helper.setBlock(grass, Blocks.GRASS_BLOCK);
            BlockPos grassAbs = helper.absolutePos(grass);
            InteractionResult onGrass = RCItems.FERTILIZER.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(grassAbs), Direction.UP, grassAbs, false)));
            helper.assertTrue(onGrass == InteractionResult.SUCCESS, "fertilizer on grass must SUCCEED, got " + onGrass);
            helper.succeed();
        });

        // The custom plants are frontier_cover, so the encroachment sweep strips them before reverting the
        // grass (the strip mechanism itself is covered in EncroachmentTests).
        RCGameTests.test("dump_plants_are_frontier_cover", 20, helper -> {
            helper.assertTrue(RCBlocks.WEEDGRASS.get().defaultBlockState().is(RCTags.FRONTIER_COVER),
                "weedgrass must be frontier_cover");
            helper.assertTrue(RCBlocks.FIREWEED.get().defaultBlockState().is(RCTags.FRONTIER_COVER),
                "fireweed must be frontier_cover");
            helper.succeed();
        });
    }
}
