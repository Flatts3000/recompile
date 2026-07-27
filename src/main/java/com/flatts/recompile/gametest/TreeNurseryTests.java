package com.flatts.recompile.gametest;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.entity.TreeNurseryBlockEntity;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * GameTests for the Tree Nursery (reclamation rung 4, spec {@code docs/tree_nursery_spec.md}). The
 * load-bearing logic is the production loop on the BlockEntity - inputs in, one chosen-species sapling
 * out over a long cook - so it is driven directly here through the BE's static entry points; the 2x2x1
 * assembly, the bucket fill, and the GUI are checked in runClient.
 */
final class TreeNurseryTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private TreeNurseryTests() {
    }

    /** A formed core in isolation - enough for the BE (the wall only gates the ticker + interaction). */
    private static TreeNurseryBlockEntity placeFormed(GameTestHelper helper) {
        helper.setBlock(POS, RCBlocks.TREE_NURSERY.get().defaultBlockState()
            .setValue(MultiblockCoreBlock.FORMED, true));
        return (TreeNurseryBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
    }

    /** Load every input: 4 Fertilizer, 4 Unknown Seedlings, a full tank. */
    private static void loadInputs(TreeNurseryBlockEntity be) {
        be.setItem(TreeNurseryBlockEntity.SLOT_FERTILIZER, new ItemStack(RCItems.FERTILIZER.get(), 4));
        be.setItem(TreeNurseryBlockEntity.SLOT_SEEDLING, new ItemStack(RCItems.UNKNOWN_SEEDLING.get(), 4));
        be.addWaterForTest(RCConfig.TREE_NURSERY_TANK_CAPACITY.get());
    }

    /** Run a full cook (one sapling's worth of ticks). */
    private static void cookOnce(TreeNurseryBlockEntity be) {
        int ticks = RCConfig.TREE_NURSERY_COOK_TICKS.get();
        for (int i = 0; i < ticks; i++) {
            be.produceTickForTest();
        }
    }

    static void register() {
        // The base case: fully supplied, a full cook raises exactly one sapling of the selected species,
        // and consumes exactly one Fertilizer, one Seedling, and waterPerSapling mB.
        RCGameTests.test("tree_nursery_raises_a_sapling", 40, helper -> {
            TreeNurseryBlockEntity be = placeFormed(helper);
            loadInputs(be);
            be.setSelectedSpecies(0);   // oak
            int water0 = be.waterStored();

            cookOnce(be);

            ItemStack out = be.getItem(TreeNurseryBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(out.is(Items.OAK_SAPLING) && out.getCount() == 1,
                "a full cook must yield one oak sapling, got " + out);
            helper.assertTrue(be.getItem(TreeNurseryBlockEntity.SLOT_FERTILIZER).getCount() == 3,
                "one Fertilizer must be consumed");
            helper.assertTrue(be.getItem(TreeNurseryBlockEntity.SLOT_SEEDLING).getCount() == 3,
                "one Seedling must be consumed");
            helper.assertTrue(water0 - be.waterStored() == RCConfig.TREE_NURSERY_WATER_PER_SAPLING.get(),
                "waterPerSapling mB must be consumed, delta was " + (water0 - be.waterStored()));
            helper.succeed();
        });

        // The chosen species is what comes out.
        RCGameTests.test("tree_nursery_honours_the_species", 40, helper -> {
            TreeNurseryBlockEntity be = placeFormed(helper);
            loadInputs(be);
            be.setSelectedSpecies(1);   // birch
            cookOnce(be);
            helper.assertTrue(be.getItem(TreeNurseryBlockEntity.SLOT_OUTPUT).is(Items.BIRCH_SAPLING),
                "selecting birch must yield a birch sapling, got " + be.getItem(TreeNurseryBlockEntity.SLOT_OUTPUT));
            helper.succeed();
        });

        // Missing water: no output, and no progress banked.
        RCGameTests.test("tree_nursery_needs_water", 40, helper -> {
            TreeNurseryBlockEntity be = placeFormed(helper);
            be.setItem(TreeNurseryBlockEntity.SLOT_FERTILIZER, new ItemStack(RCItems.FERTILIZER.get(), 4));
            be.setItem(TreeNurseryBlockEntity.SLOT_SEEDLING, new ItemStack(RCItems.UNKNOWN_SEEDLING.get(), 4));
            cookOnce(be);   // no water added
            helper.assertTrue(be.getItem(TreeNurseryBlockEntity.SLOT_OUTPUT).isEmpty(),
                "no water means no sapling");
            helper.assertTrue(be.cookProgressForTest() == 0, "no water means no cook progress");
            helper.succeed();
        });

        // Missing Fertilizer: no output.
        RCGameTests.test("tree_nursery_needs_fertilizer", 40, helper -> {
            TreeNurseryBlockEntity be = placeFormed(helper);
            be.setItem(TreeNurseryBlockEntity.SLOT_SEEDLING, new ItemStack(RCItems.UNKNOWN_SEEDLING.get(), 4));
            be.addWaterForTest(RCConfig.TREE_NURSERY_TANK_CAPACITY.get());
            cookOnce(be);
            helper.assertTrue(be.getItem(TreeNurseryBlockEntity.SLOT_OUTPUT).isEmpty(),
                "no Fertilizer means no sapling");
            helper.succeed();
        });

        // Missing Seedling: no output.
        RCGameTests.test("tree_nursery_needs_a_seedling", 40, helper -> {
            TreeNurseryBlockEntity be = placeFormed(helper);
            be.setItem(TreeNurseryBlockEntity.SLOT_FERTILIZER, new ItemStack(RCItems.FERTILIZER.get(), 4));
            be.addWaterForTest(RCConfig.TREE_NURSERY_TANK_CAPACITY.get());
            cookOnce(be);
            helper.assertTrue(be.getItem(TreeNurseryBlockEntity.SLOT_OUTPUT).isEmpty(),
                "no Seedling means no sapling");
            helper.succeed();
        });

        // A full output slot stops production - inputs are not consumed until the saplings are taken.
        RCGameTests.test("tree_nursery_stalls_on_full_output", 40, helper -> {
            TreeNurseryBlockEntity be = placeFormed(helper);
            loadInputs(be);
            be.setSelectedSpecies(0);
            be.setItem(TreeNurseryBlockEntity.SLOT_OUTPUT, new ItemStack(Items.OAK_SAPLING, 64));   // full
            cookOnce(be);
            helper.assertTrue(be.getItem(TreeNurseryBlockEntity.SLOT_FERTILIZER).getCount() == 4,
                "a full output must stop production - Fertilizer untouched");
            helper.assertTrue(be.cookProgressForTest() == 0, "a full output banks no progress");
            helper.succeed();
        });

        // Only runs while FORMED: getTicker returns null for an unformed wall, non-null once formed.
        RCGameTests.test("tree_nursery_ticks_only_when_formed", 20, helper -> {
            helper.setBlock(POS, RCBlocks.TREE_NURSERY.get());   // default = unformed
            var block = RCBlocks.TREE_NURSERY.get();
            var type = RCBlockEntities.TREE_NURSERY.get();
            helper.assertTrue(block.getTicker(helper.getLevel(), helper.getBlockState(POS), type) == null,
                "an unformed nursery must not tick");
            helper.assertTrue(block.getTicker(helper.getLevel(),
                    helper.getBlockState(POS).setValue(MultiblockCoreBlock.FORMED, true), type) != null,
                "a formed nursery must tick");
            helper.succeed();
        });

        // The water tank is exposed as a fluid capability, so a pipe or pump from a Rain Collector fills
        // it (items stay manual). Insert through the capability and confirm the tank took it.
        RCGameTests.test("tree_nursery_water_tank_is_pipe_fillable", 20, helper -> {
            TreeNurseryBlockEntity be = placeFormed(helper);
            var handler = helper.getLevel().getCapability(
                Capabilities.Fluid.BLOCK, helper.absolutePos(POS), null);
            helper.assertTrue(handler != null, "the nursery must expose its water tank as a fluid capability");
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = handler.insert(FluidResource.of(net.minecraft.world.level.material.Fluids.WATER),
                    1000, transaction);
                transaction.commit();
                helper.assertTrue(inserted == 1000, "the tank must accept water through the capability, took " + inserted);
            }
            helper.assertTrue(be.waterStored() == 1000, "the piped water must land in the tank, has " + be.waterStored());
            helper.succeed();
        });
    }
}
