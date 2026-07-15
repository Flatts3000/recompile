package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.ScrapBarrelBlock;
import com.flatts.recompile.content.block.entity.ScrapBarrelBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** GameTests for the Scrap Barrel: storage in a world with no wood. */
final class ScrapBarrelTests {

    private ScrapBarrelTests() {
    }

    static void register() {
        // The barrel is only worth having if it actually holds things and hands them back
        // when broken. Contents-dropping is inherited from BlockEntity.preRemoveSideEffects
        // (any Container drops on removal), so this guards that the block entity really is
        // a Container wired to the block - a mismatch would silently void the inventory.
        RCGameTests.test("scrap_barrel_holds_items_and_drops_them", 40, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SCRAP_BARREL.get());

            // Throws if the block entity is missing or the wrong type - so this also
            // proves the BlockEntityType is bound to the block.
            ScrapBarrelBlockEntity barrel = helper.getBlockEntity(pos, ScrapBarrelBlockEntity.class);

            helper.assertTrue(barrel.getContainerSize() == 27,
                "barrel must have 27 slots like a vanilla barrel, got " + barrel.getContainerSize());
            barrel.setItem(0, new ItemStack(RCItems.SCRAP_METAL.get(), 5));
            helper.assertTrue(barrel.getItem(0).is(RCItems.SCRAP_METAL.get()),
                "the barrel must hold what was put in it");

            // Breaking it must hand the contents back, not void them.
            helper.destroyBlock(pos);
            helper.assertBlockPresent(Blocks.AIR, pos);
            helper.succeedWhenEntityPresent(EntityType.ITEM, pos);
        });

        // Parity check with vanilla barrels, and the one deliberate deviation. A vanilla
        // barrel carries FACING and points wherever you looked; a drum stands on its end,
        // so this one is always top-up and OPEN is its only state.
        RCGameTests.test("scrap_barrel_is_always_top_up", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SCRAP_BARREL.get());
            var state = helper.getBlockState(pos);

            helper.assertTrue(state.hasProperty(ScrapBarrelBlock.OPEN),
                "the barrel needs its OPEN lid state for vanilla parity");
            helper.assertFalse(state.getValue(ScrapBarrelBlock.OPEN),
                "a freshly placed barrel must be closed");
            helper.assertTrue(state.getProperties().size() == 1,
                "OPEN must be the barrel's only state - no FACING, it is always top-up. Got: "
                    + state.getProperties());
            helper.succeed();
        });
    }
}
