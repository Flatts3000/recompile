package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * GameTests for lighting (design P1.4-A): the Oily Rag fuel and the Scrap Torch. The world has no
 * wood or coal, so torches are made from a rebar (the stick) and an oily rag (the "coal").
 */
final class LightingTests {

    private LightingTests() {
    }

    static void register() {
        // The Oily Rag is the trash world's fuel - registered at charcoal parity (the neoforge
        // furnace_fuels data map). Guards a typo'd/missing burn_time or a bad data-map path.
        RCGameTests.test("oily_rag_burns_like_charcoal", 10, helper -> {
            int burn = helper.getLevel().fuelValues().burnDuration(new ItemStack(RCItems.OILY_RAG.get()));
            helper.assertTrue(burn == 1600,
                "the oily rag must burn 1600 ticks (charcoal parity), got " + burn);
            helper.succeed();
        });

        // The Scrap Torch is a 1:1 vanilla-torch reskin, so it must emit full light (14).
        RCGameTests.test("scrap_torch_emits_full_light", 10, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SCRAP_TORCH.get());
            int light = helper.getBlockState(pos).getLightEmission();
            helper.assertTrue(light == 14,
                "the scrap torch must emit light 14 like a vanilla torch, got " + light);
            helper.succeed();
        });
    }
}
