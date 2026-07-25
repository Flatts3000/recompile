package com.flatts.recompile.gametest;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.entity.CompostHeapBlockEntity;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTests for the Compost Heap (Mod Jam - the fertilizer tier). The load-bearing logic is the layer
 * model on the BlockEntity - continuous, no fill-to-work requirement - so it is driven directly here;
 * the 2x2x2 assembly and the steam are checked in runClient.
 */
final class CompostHeapTests {

    private static final BlockPos HEAP = new BlockPos(1, 1, 1);

    private CompostHeapTests() {
    }

    /** A formed core in isolation - enough for the BE (the cage only gates the ticker + interaction). */
    private static CompostHeapBlockEntity placeFormedHeap(GameTestHelper helper) {
        helper.setBlock(HEAP, RCBlocks.COMPOST_HEAP.get().defaultBlockState()
            .setValue(MultiblockCoreBlock.FORMED, true));
        return (CompostHeapBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HEAP));
    }

    static void register() {
        // Feeding the per-layer cost forms exactly one layer; short of it, none.
        RCGameTests.test("compost_heap_forms_a_layer_at_the_cost", 20, helper -> {
            CompostHeapBlockEntity be = placeFormedHeap(helper);
            int cost = RCConfig.COMPOST_LAYER_COST.get();
            for (int i = 0; i < cost - 1; i++) {
                be.feed();
            }
            helper.assertTrue(be.layerCountForTest() == 0, "below the cost there is no layer yet");
            be.feed();   // reaches the cost
            helper.assertTrue(be.layerCountForTest() == 1, "the cost forms one layer, got " + be.layerCountForTest());
            helper.succeed();
        });

        // The vanilla-composter fix: a single finished layer harvests for one Fertilizer (no full needed).
        RCGameTests.test("compost_heap_single_layer_harvests_one_fertilizer", 20, helper -> {
            CompostHeapBlockEntity be = placeFormedHeap(helper);
            for (int i = 0; i < RCConfig.COMPOST_LAYER_COST.get(); i++) {
                be.feed();
            }
            helper.assertFalse(be.hasFinishedLayer(), "a fresh layer is not finished");
            be.ripenOldestForTest();
            helper.assertTrue(be.hasFinishedLayer(), "the layer should now be finished");

            var out = be.harvest();
            helper.assertTrue(out.is(RCItems.FERTILIZER.get()), "harvest must yield fertilizer, got " + out);
            helper.assertTrue(be.layerCountForTest() == 0, "the harvested layer is removed");
            helper.succeed();
        });

        // Full at MAX_LAYERS: refuses more input until something is harvested.
        RCGameTests.test("compost_heap_full_refuses_input", 20, helper -> {
            CompostHeapBlockEntity be = placeFormedHeap(helper);
            int cost = RCConfig.COMPOST_LAYER_COST.get();
            for (int i = 0; i < cost * CompostHeapBlockEntity.MAX_LAYERS; i++) {
                be.feed();
            }
            helper.assertTrue(be.layerCountForTest() == CompostHeapBlockEntity.MAX_LAYERS,
                "should hold MAX layers, has " + be.layerCountForTest());
            helper.assertTrue(be.isFull(), "should report full");
            helper.assertFalse(be.feed(), "a full heap refuses more input");
            helper.succeed();
        });

        // Oldest-first: with two layers, only the older (ripened) one finishes and harvests.
        RCGameTests.test("compost_heap_oldest_layer_harvests_first", 20, helper -> {
            CompostHeapBlockEntity be = placeFormedHeap(helper);
            int cost = RCConfig.COMPOST_LAYER_COST.get();
            for (int i = 0; i < cost * 2; i++) {
                be.feed();   // two layers, both fresh
            }
            helper.assertTrue(be.layerCountForTest() == 2, "two layers");
            be.ripenOldestForTest();   // ripen only the oldest (bottom)

            var out = be.harvest();
            helper.assertTrue(out.is(RCItems.FERTILIZER.get()), "the finished oldest layer harvests");
            helper.assertTrue(be.layerCountForTest() == 1, "one layer remains, still cooking");
            helper.assertFalse(be.hasFinishedLayer(), "the remaining younger layer is not finished");
            helper.succeed();
        });

        // The ticker ripens a layer over COMPOST_LAYER_TICKS.
        RCGameTests.test("compost_heap_ticks_ripen_a_layer", 20, helper -> {
            CompostHeapBlockEntity be = placeFormedHeap(helper);
            for (int i = 0; i < RCConfig.COMPOST_LAYER_COST.get(); i++) {
                be.feed();
            }
            var level = helper.getLevel();
            var pos = helper.absolutePos(HEAP);
            var state = helper.getBlockState(HEAP);
            for (int t = 0; t < RCConfig.COMPOST_LAYER_TICKS.get(); t++) {
                CompostHeapBlockEntity.serverTick(level, pos, state, be);
            }
            helper.assertTrue(be.hasFinishedLayer(), "the layer should finish after COMPOST_LAYER_TICKS");
            helper.succeed();
        });

        // Negative control: an unformed core is never ticked (getTicker returns null until formed).
        RCGameTests.test("compost_heap_unformed_does_not_tick", 20, helper -> {
            helper.setBlock(HEAP, RCBlocks.COMPOST_HEAP.get());   // default = unformed
            var block = RCBlocks.COMPOST_HEAP.get();
            var type = RCBlockEntities.COMPOST_HEAP.get();
            helper.assertTrue(
                block.getTicker(helper.getLevel(), helper.getBlockState(HEAP), type) == null,
                "an unformed compost heap must not tick");
            helper.assertTrue(
                block.getTicker(helper.getLevel(),
                    helper.getBlockState(HEAP).setValue(MultiblockCoreBlock.FORMED, true), type) != null,
                "a formed compost heap must tick");
            helper.succeed();
        });
    }
}
