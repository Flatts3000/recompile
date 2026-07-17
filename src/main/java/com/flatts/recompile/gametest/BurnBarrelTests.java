package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.BurnBarrelBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * GameTests for the Burn Barrel (design P2.2): a vanilla-furnace reskin that smelts scrap into
 * copper (the gating choice) but is deliberately manual-only - no hopper / Create automation.
 */
final class BurnBarrelTests {

    private BurnBarrelTests() {
    }

    static void register() {
        // It is a furnace: load scrap + fuel, it smelts to copper. (Slots 0=input, 1=fuel, 2=out.)
        RCGameTests.test("burn_barrel_smelts_scrap_to_copper", 250, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.BURN_BARREL.get());
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                    instanceof BurnBarrelBlockEntity barrel)) {
                helper.fail("the burn barrel has no BlockEntity");
                return;
            }
            barrel.setItem(0, new ItemStack(RCItems.SCRAP_METAL.get()));
            barrel.setItem(1, new ItemStack(RCItems.OILY_RAG.get()));
            helper.succeedWhen(() ->
                helper.assertTrue(barrel.getItem(2).is(Items.COPPER_INGOT),
                    "the burn barrel must smelt scrap metal into copper, output was " + barrel.getItem(2)));
        });

        // The whole point of "worse": no automation. A hopper below must NOT pull the output -
        // getSlotsForFace is empty on every face, so the copper stays put. Verified to FAIL if
        // the barrel exposed its slots like a normal furnace.
        RCGameTests.test("burn_barrel_blocks_a_hopper", 30, helper -> {
            BlockPos pos = new BlockPos(1, 2, 1);
            helper.setBlock(pos, RCBlocks.BURN_BARREL.get());
            helper.setBlock(pos.below(), Blocks.HOPPER);
            if (!(helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                    instanceof BurnBarrelBlockEntity barrel)) {
                helper.fail("the burn barrel has no BlockEntity");
                return;
            }
            barrel.setItem(2, new ItemStack(Items.COPPER_INGOT));
            helper.runAfterDelay(20, () -> {
                helper.assertTrue(barrel.getItem(2).is(Items.COPPER_INGOT),
                    "a hopper must not pull from the burn barrel - it is manual-only");
                helper.succeed();
            });
        });
    }
}
