package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.SortingTarpBlock;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** GameTests for the Sorting Tarp manual sift-into-world mechanic (design P1.3, revised 2026-07-14). */
final class SortingTarpTests {

    private SortingTarpTests() {
    }

    static void register() {
        registerRateParity();
        registerInteractions();
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
     * The clicks themselves, rather than the sift underneath them.
     *
     * <p>{@code siftInput} is the static entry point the two tests above use, and it is downstream of
     * every guard the block actually has: the {@code #recompile:binnable} filter, the
     * {@code reachesStorage} gate on the file-all, the hold-to-repeat cooldown, and the creative
     * no-charge. All four live in {@code useItemOn} / {@code useWithoutItem}, none of them was reached
     * from a test, and each of them fails as a gameplay bug rather than as an exception.
     *
     * <p>The routing primitive underneath is proven in {@code ScrapNetworkTests}; what is proven here is
     * the tarp's own decision about what to hand it. That matters more than usual, because the file-all
     * is one of exactly TWO callers in the mod that pass {@code autoBind=true} - it binds empty bins.
     */
    private static void registerInteractions() {
        // Filing takes the scrap and leaves everything else alone. Without the #binnable filter one
        // shift-click posts the player's pickaxe, blueprints and food into the bins along with the junk,
        // and the only way back out is the panel, one item at a time. Worse than losing them: it also
        // BINDS an empty bin to a pickaxe, and a bound bin holds one material forever.
        RCGameTests.test("filing_at_a_tarp_takes_only_binnable_scrap", 20, helper -> {
            BlockPos tarp = new BlockPos(1, 1, 1);
            helper.setBlock(tarp, RCBlocks.SORTING_TARP.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().add(new ItemStack(RCItems.SCRAP_METAL.get(), 12));
            player.getInventory().add(new ItemStack(Items.DIAMOND_PICKAXE));
            player.setShiftKeyDown(true);   // the file-all is the SECONDARY use

            InteractionResult result = clickEmptyHanded(helper, player, tarp);

            helper.assertTrue(result == InteractionResult.SUCCESS,
                "a file-all into connected storage must report SUCCESS, got " + result);
            helper.assertTrue(bin.boundMaterial() == RCItems.SCRAP_METAL.get(),
                "the empty bin must bind to the scrap that was filed, bound to " + bin.boundMaterial());
            helper.assertTrue(bin.amount() == 12,
                "all twelve scrap metal must be filed, bin has " + bin.amount());
            helper.assertTrue(countIn(player, RCItems.SCRAP_METAL.get()) == 0,
                "the filed scrap must leave the inventory");
            helper.assertTrue(countIn(player, Items.DIAMOND_PICKAXE) == 1,
                "a pickaxe is not #recompile:binnable and must stay in the player's hands");
            helper.succeed();
        });

        // The opposite, so the filter test above cannot pass by the tarp simply doing nothing: with no
        // storage in the cluster the click must PASS rather than report SUCCESS. PASS is what lets the
        // interaction fall through to whatever else would have handled it; a SUCCESS from a tarp that
        // filed nothing is a click the player spent and cannot tell apart from one that worked.
        RCGameTests.test("filing_at_an_unwired_tarp_passes_the_click_through", 20, helper -> {
            BlockPos tarp = new BlockPos(1, 1, 1);
            helper.setBlock(tarp, RCBlocks.SORTING_TARP.get());   // no bin, no barrel

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().add(new ItemStack(RCItems.SCRAP_METAL.get(), 12));
            player.setShiftKeyDown(true);

            InteractionResult result = clickEmptyHanded(helper, player, tarp);

            helper.assertTrue(result == InteractionResult.PASS,
                "a tarp with no storage in its cluster must pass the click through, got " + result);
            helper.assertTrue(countIn(player, RCItems.SCRAP_METAL.get()) == 12,
                "nothing may leave the inventory when there is nowhere for it to go, holds "
                    + countIn(player, RCItems.SCRAP_METAL.get()));
            helper.succeed();
        });

        // Holding something the tarp cannot sift must ask for an empty hand rather than claim the click.
        // TRY_WITH_EMPTY_HAND is what routes a player holding a torch into the empty-handed file-all;
        // returning SUCCESS here spends the click and runs a pull table lookup for an item that has none.
        RCGameTests.test("a_tarp_refuses_to_sift_something_that_is_not_garbage", 20, helper -> {
            BlockPos tarp = new BlockPos(1, 1, 1);
            helper.setBlock(tarp, RCBlocks.SORTING_TARP.get());

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            ItemStack held = new ItemStack(Items.DIAMOND, 4);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);

            InteractionResult result = clickHolding(helper, player, tarp, held);

            helper.assertTrue(result == InteractionResult.TRY_WITH_EMPTY_HAND,
                "a non-sortable in hand must fall through to the empty-hand path, got " + result);
            helper.assertTrue(held.getCount() == 4, "nothing may be consumed, hand holds " + held.getCount());
            helper.assertEntityNotPresent(EntityType.ITEM);
            helper.succeed();
        });

        // A survival sift costs exactly one block, and the cooldown paces the auto-repeat. The shrink is
        // the ONLY price the tarp charges - without it one Block of Garbage sifts the household stream
        // forever. Without the cooldown, holding right-click puts a whole stack through in a single tick,
        // which is what the pacing was added for: a bale is twelve loot rolls per sift.
        RCGameTests.test("a_survival_sift_spends_one_block_and_paces_the_next", 20, helper -> {
            BlockPos tarp = new BlockPos(1, 1, 1);
            helper.setBlock(tarp, RCBlocks.SORTING_TARP.get());

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            ItemStack held = new ItemStack(RCItems.GARBAGE_BLOCK.get(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);

            InteractionResult first = clickHolding(helper, player, tarp, held);
            helper.assertTrue(first == InteractionResult.SUCCESS, "the first sift must succeed, got " + first);
            helper.assertTrue(held.getCount() == 2,
                "one sift spends exactly one block, hand holds " + held.getCount());
            helper.assertEntityPresent(EntityType.ITEM);

            // Same tick, so the cooldown has not expired: the repeat is swallowed, not charged for.
            InteractionResult repeat = clickHolding(helper, player, tarp, held);
            helper.assertTrue(repeat == InteractionResult.SUCCESS,
                "the paced repeat still consumes the click, got " + repeat);
            helper.assertTrue(held.getCount() == 2,
                "a click inside the cooldown must not spend a second block, hand holds " + held.getCount());
            helper.succeed();
        });

        // ...and the creative branch, which is the one a test written with a bare
        // makeMockServerPlayerInLevel exercises by accident. Asserting the sift still HAPPENED is what
        // keeps this from passing on a tarp that did nothing at all.
        RCGameTests.test("a_creative_sift_does_not_spend_the_block", 20, helper -> {
            BlockPos tarp = new BlockPos(1, 1, 1);
            helper.setBlock(tarp, RCBlocks.SORTING_TARP.get());

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.CREATIVE);
            ItemStack held = new ItemStack(RCItems.GARBAGE_BLOCK.get(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);

            clickHolding(helper, player, tarp, held);

            helper.assertEntityPresent(EntityType.ITEM);
            helper.assertTrue(held.getCount() == 3,
                "a creative player is not charged for a sift, hand holds " + held.getCount());
            helper.succeed();
        });
    }

    /** Right-click the tarp with {@code held}, exactly as the player's hand does it. */
    private static InteractionResult clickHolding(GameTestHelper helper, ServerPlayer player,
            BlockPos tarp, ItemStack held) {
        BlockPos abs = helper.absolutePos(tarp);
        return helper.getLevel().getBlockState(abs).useItemOn(held, helper.getLevel(), player,
            InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    /** Right-click the tarp empty-handed. */
    private static InteractionResult clickEmptyHanded(GameTestHelper helper, ServerPlayer player,
            BlockPos tarp) {
        BlockPos abs = helper.absolutePos(tarp);
        return helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player,
            new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    private static ScrapBinBlockEntity placeBin(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RCBlocks.SCRAP_BIN.get());
        return (ScrapBinBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    private static int countIn(ServerPlayer player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
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
