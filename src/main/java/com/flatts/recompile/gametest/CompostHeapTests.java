package com.flatts.recompile.gametest;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.CompostHeapCoreBlock;
import com.flatts.recompile.content.block.entity.CompostHeapBlockEntity;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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

    /** Build the real 2x2x2 from the core + seven Machine Frames and form it; returns the core BE. */
    private static CompostHeapBlockEntity placeAndFormFullHeap(GameTestHelper helper, BlockPos core) {
        helper.setBlock(core, RCBlocks.COMPOST_HEAP.get());
        for (Vec3i off : List.of(new Vec3i(1, 0, 0), new Vec3i(0, 0, 1), new Vec3i(1, 0, 1),
                new Vec3i(0, 1, 0), new Vec3i(1, 1, 0), new Vec3i(0, 1, 1), new Vec3i(1, 1, 1))) {
            helper.setBlock(core.offset(off), RCBlocks.MACHINE_FRAME.get());
        }
        helper.assertTrue(MultiblockCoreBlock.tryForm(helper.getLevel(), helper.absolutePos(core)),
            "the 2x2x2 must form from the seven Machine Frames");
        return (CompostHeapBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(core));
    }

    /** Total Machine Frames lying as item entities near the core - what a break returned. */
    private static int framesDropped(GameTestHelper helper, BlockPos coreAbs) {
        return itemsDropped(helper, coreAbs, RCItems.MACHINE_FRAME.get());
    }

    /** Total of a given item lying as entities near the core. */
    private static int itemsDropped(GameTestHelper helper, BlockPos coreAbs, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(coreAbs).inflate(8))) {
            if (e.getItem().is(item)) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    /** Total Machine Frames in a player's inventory. */
    private static int framesHeld(ServerPlayer player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(RCItems.MACHINE_FRAME.get())) {
                n += stack.getCount();
            }
        }
        return n;
    }

    static void register() {
        // REPRO (dupe glitch): placing the core while carrying the parts auto-assembles the 2x2x2 and
        // must consume EXACTLY seven Machine Frames from the inventory - the real setPlacedBy path the
        // tryForm-based helper never exercised. A build that under-consumes + a break that returns seven
        // is the duplication.
        RCGameTests.test("compost_heap_autoassemble_consumes_seven", 40, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            var level = helper.getLevel();
            BlockPos abs = helper.absolutePos(core);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);   // real players build in survival
            player.getInventory().add(new ItemStack(RCItems.MACHINE_FRAME.get(), 32));
            int before = framesHeld(player);
            level.setBlock(abs, RCBlocks.COMPOST_HEAP.get().defaultBlockState(), 3);
            RCBlocks.COMPOST_HEAP.get().setPlacedBy(level, abs, level.getBlockState(abs), player,
                new ItemStack(RCBlocks.COMPOST_HEAP.get()));
            int consumed = before - framesHeld(player);
            helper.assertTrue(MultiblockCoreBlock.isFormed(level.getBlockState(abs)),
                "the heap must auto-assemble and form, consumed " + consumed);
            helper.assertTrue(consumed == 7,
                "auto-assemble must consume exactly 7 Machine Frames, consumed " + consumed);
            helper.succeed();
        });

        // REPRO (dupe glitch): the full round-trip - carry parts, place (auto-assemble), break the core -
        // and the inventory+drops must net to what you started with. This is the exact loop in the clip.
        RCGameTests.test("compost_heap_place_then_break_conserves", 60, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            var level = helper.getLevel();
            BlockPos abs = helper.absolutePos(core);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);   // real players build in survival
            player.getInventory().add(new ItemStack(RCItems.MACHINE_FRAME.get(), 32));
            int before = framesHeld(player);
            level.setBlock(abs, RCBlocks.COMPOST_HEAP.get().defaultBlockState(), 3);
            RCBlocks.COMPOST_HEAP.get().setPlacedBy(level, abs, level.getBlockState(abs), player,
                new ItemStack(RCBlocks.COMPOST_HEAP.get()));
            level.destroyBlock(abs, true);
            int net = framesHeld(player) + framesDropped(helper, abs);   // held after + dropped on break
            helper.assertTrue(net == before,
                "place+break must conserve Machine Frames: started " + before + ", ended " + net);
            helper.succeed();
        });

        // REPRO (dupe glitch): breaking the CORE of a formed 2x2x2 must return exactly the seven
        // Machine Frames it was built from - no more. Uses the real destroyBlock path (drops + the
        // removal handlers), which is what the BE-only tests never exercised.
        RCGameTests.test("compost_heap_break_core_returns_seven_frames", 40, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            placeAndFormFullHeap(helper, core);
            BlockPos coreAbs = helper.absolutePos(core);
            helper.getLevel().destroyBlock(coreAbs, true);
            int frames = framesDropped(helper, coreAbs);
            int cores = itemsDropped(helper, coreAbs, RCBlocks.COMPOST_HEAP.get().asItem());
            helper.assertTrue(frames == 7, "breaking the core must return exactly 7 Machine Frames, got " + frames);
            helper.assertTrue(cores == 1, "breaking the core must return exactly 1 Compost Heap core, got " + cores);
            helper.succeed();
        });

        // REPRO (dupe glitch): breaking a CAGE cell (the dummy path) must also return exactly seven.
        RCGameTests.test("compost_heap_break_cage_returns_seven_frames", 40, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            placeAndFormFullHeap(helper, core);
            BlockPos coreAbs = helper.absolutePos(core);
            helper.getLevel().destroyBlock(helper.absolutePos(core.offset(1, 0, 0)), true);
            int frames = framesDropped(helper, coreAbs);
            int cores = itemsDropped(helper, coreAbs, RCBlocks.COMPOST_HEAP.get().asItem());
            helper.assertTrue(frames == 7, "breaking a cage cell must return exactly 7 Machine Frames, got " + frames);
            helper.assertTrue(cores == 1, "breaking a cage cell must return exactly 1 Compost Heap core, got " + cores);
            helper.succeed();
        });

        // REPRO (dupe glitch): the same, but the machine was assembled the REAL way (setPlacedBy from a
        // survival inventory) rather than via tryForm. If the disband re-entrancy differs by how the
        // machine formed, this catches it. Breaking a cage must still return exactly 1 core + 7 frames.
        RCGameTests.test("compost_heap_autoassembled_break_cage_no_dupe", 60, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            var level = helper.getLevel();
            BlockPos abs = helper.absolutePos(core);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.getInventory().add(new ItemStack(RCItems.MACHINE_FRAME.get(), 32));
            level.setBlock(abs, RCBlocks.COMPOST_HEAP.get().defaultBlockState(), 3);
            RCBlocks.COMPOST_HEAP.get().setPlacedBy(level, abs, level.getBlockState(abs), player,
                new ItemStack(RCBlocks.COMPOST_HEAP.get()));
            helper.assertTrue(MultiblockCoreBlock.isFormed(level.getBlockState(abs)), "must be formed");
            level.destroyBlock(helper.absolutePos(core.offset(1, 0, 0)), true);
            int frames = framesDropped(helper, abs);
            int cores = itemsDropped(helper, abs, RCBlocks.COMPOST_HEAP.get().asItem());
            helper.assertTrue(frames == 7, "auto-assembled cage break must return 7 frames, got " + frames);
            helper.assertTrue(cores == 1, "auto-assembled cage break must return 1 core, got " + cores);
            helper.succeed();
        });

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

        // Fill reads on the core blockstate: an empty heap is FILL 0, one fed layer is FILL 1, a full
        // heap is FILL MAX. The core's compost-column model follows FILL, so this is what makes the
        // layers countable through the wire.
        RCGameTests.test("compost_heap_fill_reads_on_the_blockstate", 20, helper -> {
            CompostHeapBlockEntity be = placeFormedHeap(helper);
            helper.assertTrue(helper.getBlockState(HEAP).getValue(CompostHeapCoreBlock.FILL) == 0,
                "an empty heap must read FILL 0, got " + helper.getBlockState(HEAP).getValue(CompostHeapCoreBlock.FILL));
            int cost = RCConfig.COMPOST_LAYER_COST.get();
            for (int i = 0; i < cost; i++) {
                be.feed();   // one layer
            }
            helper.assertTrue(helper.getBlockState(HEAP).getValue(CompostHeapCoreBlock.FILL) == 1,
                "one fed layer must read FILL 1, got " + helper.getBlockState(HEAP).getValue(CompostHeapCoreBlock.FILL));
            for (int i = 0; i < cost * CompostHeapBlockEntity.MAX_LAYERS; i++) {
                be.feed();   // fill it
            }
            helper.assertTrue(helper.getBlockState(HEAP).getValue(CompostHeapCoreBlock.FILL) == CompostHeapBlockEntity.MAX_LAYERS,
                "a full heap must read FILL MAX, got " + helper.getBlockState(HEAP).getValue(CompostHeapCoreBlock.FILL));
            helper.succeed();
        });

        // Ripe layers read on the core blockstate (RIPE = the finished bottom prefix), so the bottom
        // bands can render with the finished-compost texture.
        RCGameTests.test("compost_heap_ripe_reads_on_the_blockstate", 20, helper -> {
            CompostHeapBlockEntity be = placeFormedHeap(helper);
            for (int i = 0; i < RCConfig.COMPOST_LAYER_COST.get() * 2; i++) {
                be.feed();   // two fresh layers
            }
            helper.assertTrue(helper.getBlockState(HEAP).getValue(CompostHeapCoreBlock.RIPE) == 0,
                "fresh layers are not ripe, got " + helper.getBlockState(HEAP).getValue(CompostHeapCoreBlock.RIPE));
            be.ripenOldestForTest();
            helper.assertTrue(helper.getBlockState(HEAP).getValue(CompostHeapCoreBlock.RIPE) == 1,
                "one ripe layer must read RIPE 1, got " + helper.getBlockState(HEAP).getValue(CompostHeapCoreBlock.RIPE));
            helper.succeed();
        });

        // The real interaction path (the BE-only tests never exercise it): right-clicking a CAGE cell
        // with muck feeds the core through the dummy redirect, and an empty-handed right-click on a cage
        // cell harvests a ripe layer. This is what "right-click does nothing" would fail on.
        RCGameTests.test("compost_heap_right_click_feeds_and_harvests", 40, helper -> {
            BlockPos core = new BlockPos(1, 1, 1);
            CompostHeapBlockEntity be = placeAndFormFullHeap(helper, core);

            BlockPos cageAbs = helper.absolutePos(core.offset(1, 0, 0));   // a dummy cage cell, not the core
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cageAbs), Direction.UP, cageAbs, false);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();

            int cost = RCConfig.COMPOST_LAYER_COST.get();
            for (int i = 0; i < cost; i++) {
                ItemStack muck = new ItemStack(RCItems.ORGANIC_MUCK.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, muck);
                helper.getLevel().getBlockState(cageAbs)
                    .useItemOn(muck, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            }
            helper.assertTrue(be.layers() == 1,
                "right-clicking a cage cell with muck must feed the core through the redirect, layers=" + be.layers());

            be.ripenOldestForTest();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            // The game reaches useWithoutItem (the harvest) only if useItemOn returns TRY_WITH_EMPTY_HAND;
            // plain PASS would strand an empty-handed click and Fertilizer could never be pulled.
            InteractionResult empty = helper.getLevel().getBlockState(cageAbs)
                .useItemOn(ItemStack.EMPTY, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(empty == InteractionResult.TRY_WITH_EMPTY_HAND,
                "empty-handed useItemOn must fall through to the harvest, got " + empty);
            helper.getLevel().getBlockState(cageAbs).useWithoutItem(helper.getLevel(), player, hit);
            helper.assertTrue(be.layers() == 0,
                "an empty-handed right-click on a cage cell must harvest the ripe layer, layers=" + be.layers());
            // The produce turns out into the world (popResourceFromFace), like the vanilla composter's
            // bonemeal - not into the player's inventory. It pops out the clicked face of the core.
            helper.assertItemEntityPresent(RCItems.FERTILIZER.get(), core, 3.0);
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
