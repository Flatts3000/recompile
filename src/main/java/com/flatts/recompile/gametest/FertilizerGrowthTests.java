package com.flatts.recompile.gametest;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fertilizer as this world's bone meal (#71).
 *
 * <p>There is no bone meal here and there cannot be: it comes from skeletons, and the starting biome is
 * deliberately creature-free (P1.9). So before this, nothing could hurry a crop or a tree along at all.
 *
 * <p>The regression worth guarding is not the new behaviour, it is the old one. Grass is itself a
 * {@code BonemealableBlock}, so if the fallback were ever reached before the grass branch, the mod's
 * rippling weeds-and-wildflowers scatter would be silently replaced by vanilla's flower burst and the
 * whole Vegetation tier would look like plain bone meal. Nothing about that would throw.
 */
final class FertilizerGrowthTests {

    private FertilizerGrowthTests() {
    }

    /** Use a Fertilizer on a block the way a right-click does, and report whether it was consumed. */
    private static boolean useFertilizerOn(GameTestHelper helper, BlockPos rel) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(RCItems.FERTILIZER.get(), 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(rel);
        stack.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack,
            new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
        boolean consumed = stack.getCount() < 4;
        player.discard();
        return consumed;
    }

    static void register() {
        // The point of the feature: a planted crop can be hurried.
        RCGameTests.test("fertilizer_advances_a_crop", 40, helper -> {
            BlockPos dirt = new BlockPos(1, 1, 1);
            BlockPos crop = dirt.above();
            helper.setBlock(dirt, Blocks.FARMLAND);
            helper.setBlock(crop, Blocks.WHEAT);

            BlockState before = helper.getBlockState(crop);
            int ageBefore = before.getValue(CropBlock.AGE);
            helper.assertTrue(ageBefore == 0, "the crop should start at age 0, was " + ageBefore);

            boolean consumed = useFertilizerOn(helper, crop);

            int ageAfter = helper.getBlockState(crop).getValue(CropBlock.AGE);
            helper.assertTrue(ageAfter > ageBefore,
                "fertilizer must advance a crop: age went " + ageBefore + " -> " + ageAfter);
            helper.assertTrue(consumed, "a fertilizer that grew something must be consumed");
            helper.succeed();
        });

        // Saplings are the other half of the issue, and the only ones a player ever meets are the ones
        // the Tree Nursery planted - StripSaplingsModifier means one can never be held. Growth from a
        // sapling is random, so this asserts the sapling was ACCEPTED as a target and the fertilizer
        // paid for it, not that a tree appeared.
        RCGameTests.test("fertilizer_is_spent_on_a_sapling", 40, helper -> {
            BlockPos ground = new BlockPos(3, 1, 1);
            BlockPos sapling = ground.above();
            helper.setBlock(ground, Blocks.DIRT);
            helper.setBlock(sapling, Blocks.OAK_SAPLING);

            helper.assertTrue(useFertilizerOn(helper, sapling),
                "a sapling must be a valid fertilizer target - it is the only thing the Tree Nursery "
                    + "plants and the player cannot hold one");
            helper.succeed();
        });

        // Negative control: fertilizer on something that does not grow must not vanish. Without this,
        // "consumed" above proves nothing - an item that is always eaten would pass it.
        RCGameTests.test("fertilizer_is_not_wasted_on_bare_ground", 40, helper -> {
            BlockPos stone = new BlockPos(5, 1, 1);
            helper.setBlock(stone, Blocks.STONE);
            helper.assertFalse(useFertilizerOn(helper, stone),
                "fertilizer must not be consumed by a block it cannot grow");
            helper.succeed();
        });

        // The config gate, per the standing "everything ships config-gated" rule.
        RCGameTests.test("fertilizer_growth_respects_its_config", 40, helper -> {
            BlockPos dirt = new BlockPos(7, 1, 1);
            BlockPos crop = dirt.above();
            helper.setBlock(dirt, Blocks.FARMLAND);
            helper.setBlock(crop, Blocks.WHEAT);

            boolean was = RCConfig.FERTILIZER_GROWTH_ENABLED.get();
            try {
                RCConfig.FERTILIZER_GROWTH_ENABLED.set(false);
                helper.assertFalse(useFertilizerOn(helper, crop),
                    "with the config off, fertilizer must not be spent on a crop");
                helper.assertTrue(helper.getBlockState(crop).getValue(CropBlock.AGE) == 0,
                    "with the config off, the crop must not advance");
            } finally {
                // Restore in a finally: the config is global and leaking it off would disable growth
                // for every test that ran afterwards.
                RCConfig.FERTILIZER_GROWTH_ENABLED.set(was);
            }
            helper.succeed();
        });

        // THE REGRESSION. Grass is a BonemealableBlock, so a fallback reached too early would replace
        // the mod's scatter with vanilla's flower burst - and it would look fine. Vanilla bonemeal on
        // grass places plants IMMEDIATELY; the mod's scatter is scheduled and ripples over seconds, so
        // "nothing has appeared yet on the tick after the click" is exactly what distinguishes them.
        RCGameTests.test("fertilizer_on_grass_still_uses_the_mod_scatter", 40, helper -> {
            BlockPos grass = new BlockPos(9, 1, 1);
            helper.setBlock(grass, Blocks.GRASS_BLOCK);
            helper.setBlock(grass.above(), Blocks.AIR);

            helper.assertTrue(useFertilizerOn(helper, grass), "fertilizer on grass must be consumed");
            helper.assertBlockPresent(Blocks.AIR, grass.above());
            helper.succeed();
        });
    }
}
