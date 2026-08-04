package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.SortingTarpBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/** GameTests for the Sorting Tarp manual sift-into-world mechanic (design P1.3, revised 2026-07-14). */
final class SortingTarpTests {

    private SortingTarpTests() {
    }

    static void register() {
        registerRateParity();
        // Place a tarp, sift one garbage block through it, and assert sorted material
        // item entities dropped into the world (stateless: no inventory, no GUI).
        RCGameTests.test("sorting_tarp_sifts_into_world", 60, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SORTING_TARP.get());

            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);

            SortingTarpBlock.siftInput(level, abs, RCItems.GARBAGE_BLOCK.get());

            helper.assertEntityPresent(EntityType.ITEM);
            helper.succeed();
        });

        // #68: the yard's material sifts too. Asserting the drops are SHARDS is the point - the tarp
        // routes a pull table per input, so sifting rubble against household_pulls would still drop
        // items and still pass an "something dropped" check while handing out the wrong economy.
        RCGameTests.test("sorting_tarp_sifts_stone_rubble", 60, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SORTING_TARP.get());
            ServerLevel level = helper.getLevel();
            BlockPos abs = helper.absolutePos(pos);

            SortingTarpBlock.siftInput(level, abs, RCItems.STONE_RUBBLE.get());

            List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(4));
            helper.assertTrue(!dropped.isEmpty(), "sifting rubble must drop something");
            for (ItemEntity e : dropped) {
                helper.assertTrue(e.getItem().is(RCTags.STONE_SHARDS),
                    "rubble must sift into stone shards, got " + e.getItem().getItem());
            }
            helper.succeed();
        });
    }
    /**
     * The rate promise, asserted rather than trusted.
     *
     * <p>"The Separator yields exactly what the tarp yields" is only true because both read
     * {@link SortableBlock#sortRolls}. This checks the shared function actually covers every sortable
     * the game has - the way that claim would break is not two different numbers, it is a new sortable
     * variant that one machine knows about and the other does not.
     */
    private static void registerRateParity() {
        RCGameTests.test("every_sortable_has_a_machine_rate", 20, helper -> {
            List<String> missing = new ArrayList<>();
            int checked = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                if (!(block instanceof SortableBlock)) {
                    continue;
                }
                if (!Recompile.MOD_ID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace())) {
                    continue;
                }
                checked++;
                Item item = block.asItem();
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                if (SortableBlock.sortRolls(item) <= 0) {
                    missing.add(id + " has no machine rate");
                }
                if (SortableBlock.pullTableFor(item) == null) {
                    missing.add(id + " resolves to no pull table");
                }
            }
            helper.assertTrue(checked >= 5,
                "only " + checked + " sortable blocks found - the mod ships five, so discovery is "
                    + "broken and this would pass against a variant with no rate at all");
            helper.assertTrue(missing.isEmpty(),
                "sortables the machines cannot process (" + missing.size() + "): " + missing);
            helper.succeed();
        });

        // Mechanical Waste's 8 is DERIVED, not picked: it shares the Compacted Bale's 3-4 crumble
        // window, so it shares the bale's hand average, so it takes the bale's number. If someone
        // retunes one window without the other, this says so.
        RCGameTests.test("mechanical_waste_sorts_at_the_bale_rate", 20, helper -> {
            int bale = SortableBlock.sortRolls(RCItems.COMPACTED_BALE.get().asItem());
            int waste = SortableBlock.sortRolls(RCItems.MECHANICAL_WASTE.get().asItem());
            helper.assertTrue(waste == bale,
                "Mechanical Waste sorts at " + waste + " and the Compacted Bale at " + bale
                    + ". They share a 3-4 crumble window, so they share a hand average, so they must "
                    + "share a machine rate - change one window and you must re-derive, not guess");
            helper.succeed();
        });
    }

}
