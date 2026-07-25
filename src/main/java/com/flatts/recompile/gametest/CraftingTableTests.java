package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests for the Scrap Crafting Table: vanilla-parity (the menu stays valid over the block) plus
 * craft-from-storage (design P2.10 flow 4) - the grid restocks from the connected scrap network, so a
 * shift-craft pulls a whole run out of the bins. The refill is the server-side core and is driven
 * directly here; the connected-storage panel is a client screen, checked in runClient.
 */
final class CraftingTableTests {

    private static final BlockPos TABLE = new BlockPos(1, 1, 1);
    private static final int FIRST_GRID_SLOT = 1; // CraftingMenu: 0 = result, 1..9 = grid

    private CraftingTableTests() {
    }

    static void register() {
        // Regression: the table opened a plain vanilla CraftingMenu, whose stillValid hard-codes
        // Blocks.CRAFTING_TABLE. It failed on the first tick over a scrap table, so the menu shut
        // instantly and right-clicking looked inert. Assert the menu validates over its own block.
        RCGameTests.test("scrap_crafting_table_menu_stays_open", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            Player player = helper.makeMockPlayer(GameType.CREATIVE);
            Vec3 standing = helper.absoluteVec(TABLE.above().getCenter());
            player.snapTo(standing.x, standing.y, standing.z);

            ScrapCraftingStationMenu menu = openMenu(helper, player);
            helper.assertTrue(menu.stillValid(player),
                "crafting menu must stay valid over a scrap crafting table");

            helper.setBlock(TABLE, Blocks.AIR);
            helper.assertFalse(menu.stillValid(player),
                "crafting menu must close once the table is gone");
            helper.succeed();
        });

        // Craft-from-storage: an emptied grid slot restocks from a connected bound bin.
        RCGameTests.test("scrap_crafting_table_refills_grid_from_a_bin", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 10));

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            // Prime a grid slot, capture the pattern, then empty it as a craft would.
            menu.getSlot(FIRST_GRID_SLOT).set(new ItemStack(RCItems.SCRAP_METAL.get()));
            Item[] pattern = menu.capturePatternForTest();
            menu.getSlot(FIRST_GRID_SLOT).set(ItemStack.EMPTY);

            menu.refillGridForTest(player, pattern);

            helper.assertTrue(menu.getSlot(FIRST_GRID_SLOT).getItem().is(RCItems.SCRAP_METAL.get()),
                "the emptied grid slot must restock scrap metal from the bin");
            helper.assertTrue(bin.amount() == 9, "the bin should have given up one, has " + bin.amount());
            helper.succeed();
        });

        // Order: a bound bin is drained before the player's own inventory (bins -> barrel -> inventory).
        RCGameTests.test("scrap_crafting_table_refill_prefers_the_bin_over_inventory", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 4));

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.getInventory().add(new ItemStack(RCItems.SCRAP_METAL.get(), 8));
            int invBefore = countIn(player, RCItems.SCRAP_METAL.get());
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            menu.getSlot(FIRST_GRID_SLOT).set(new ItemStack(RCItems.SCRAP_METAL.get()));
            Item[] pattern = menu.capturePatternForTest();
            menu.getSlot(FIRST_GRID_SLOT).set(ItemStack.EMPTY);
            menu.refillGridForTest(player, pattern);

            helper.assertTrue(bin.amount() == 3, "the bin should be drained first, has " + bin.amount());
            helper.assertTrue(countIn(player, RCItems.SCRAP_METAL.get()) == invBefore,
                "the inventory must be untouched while the bin has stock");
            helper.succeed();
        });

        // End-to-end through the real quick-move loop: a shift-craft bulk-crafts straight from a bin,
        // restocking the grid between crafts. This is what refillGrid-in-isolation cannot prove - it
        // pins the ordering (refill must run AFTER onTake empties the grid, or the run makes one item).
        // scrap_plating is 4 scrap_metal -> 1, a single-material recipe, so the math is exact.
        RCGameTests.test("scrap_crafting_table_bulk_crafts_from_a_bin", 40, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 40));

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ScrapCraftingStationMenu menu = openMenu(helper, player);
            // Fill the top-left 2x2 (menu slots 1,2,4,5) with scrap metal - the shaped recipe pattern.
            for (int gridSlot : new int[] {1, 2, 4, 5}) {
                menu.getSlot(gridSlot).set(new ItemStack(RCItems.SCRAP_METAL.get()));
            }
            helper.assertTrue(menu.getSlot(0).getItem().is(RCItems.SCRAP_PLATING.get()),
                "the grid must produce scrap plating, got " + menu.getSlot(0).getItem());

            int crafts = 0;
            while (menu.getSlot(0).hasItem() && crafts < 200) {
                ItemStack out = menu.quickMoveStack(player, 0);
                if (out.isEmpty()) {
                    break;
                }
                crafts++;
            }

            // 4 in the grid + 40 in the bin = 44 scrap metal = 11 crafts; the bin ends empty.
            helper.assertTrue(crafts == 11, "should bulk-craft 11 plating from grid+bin, got " + crafts);
            helper.assertTrue(bin.amount() == 0, "the bin should be fully drained, has " + bin.amount());
            helper.assertTrue(countIn(player, RCItems.SCRAP_PLATING.get()) == 11,
                "the player should hold 11 scrap plating, has " + countIn(player, RCItems.SCRAP_PLATING.get()));
            helper.succeed();
        });

        // Panel withdraw: clicking a material pulls a stack of it out of the network into the player.
        // Driven through clickMenuButton (the button id is the item's registry id), as the screen does.
        RCGameTests.test("scrap_crafting_table_panel_withdraws_a_stack", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 100));

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            int id = BuiltInRegistries.ITEM.getId(RCItems.SCRAP_METAL.get());
            boolean handled = menu.clickMenuButton(player, id);

            helper.assertTrue(handled, "clicking a stocked material must withdraw");
            helper.assertTrue(countIn(player, RCItems.SCRAP_METAL.get()) == 64,
                "should pull a full stack, player has " + countIn(player, RCItems.SCRAP_METAL.get()));
            helper.assertTrue(bin.amount() == 36, "the bin should drop by a stack, has " + bin.amount());
            helper.succeed();
        });

        // Panel deposit: with a stack on the cursor, the deposit button stores it into the network,
        // auto-binding an empty bin. Mirrors the withdraw; driven through clickMenuButton like the screen.
        RCGameTests.test("scrap_crafting_table_panel_deposits_the_cursor", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));   // empty, unbound
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            menu.setCarried(new ItemStack(RCItems.SCRAP_METAL.get(), 30));
            boolean handled = menu.clickMenuButton(player, ScrapCraftingStationMenu.DEPOSIT_BUTTON);

            helper.assertTrue(handled, "depositing a carried stack into an empty bin must succeed");
            helper.assertTrue(bin.boundMaterial() == RCItems.SCRAP_METAL.get(), "the empty bin should bind to metal");
            helper.assertTrue(bin.amount() == 30, "the bin should hold the deposited 30, has " + bin.amount());
            helper.assertTrue(menu.getCarried().isEmpty(), "the cursor should be emptied by the deposit");
            helper.succeed();
        });

        // Negative control: depositing with an empty cursor, or no storage, is a no-op.
        RCGameTests.test("scrap_crafting_table_panel_deposit_needs_storage", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());   // no bins/barrel connected
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            menu.setCarried(new ItemStack(RCItems.SCRAP_METAL.get(), 30));
            boolean handled = menu.clickMenuButton(player, ScrapCraftingStationMenu.DEPOSIT_BUTTON);

            helper.assertFalse(handled, "with no connected storage the deposit must be a no-op");
            helper.assertTrue(menu.getCarried().getCount() == 30, "the cursor stack must be untouched");
            helper.succeed();
        });

        // Negative control: withdrawing a material the network does not hold does nothing.
        RCGameTests.test("scrap_crafting_table_panel_withdraw_needs_stock", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            placeBin(helper, new BlockPos(2, 1, 1));   // empty, unbound
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            int id = BuiltInRegistries.ITEM.getId(RCItems.SCRAP_METAL.get());
            boolean handled = menu.clickMenuButton(player, id);

            helper.assertFalse(handled, "withdrawing a material with no stock must be a no-op");
            helper.assertTrue(countIn(player, RCItems.SCRAP_METAL.get()) == 0, "nothing should be given");
            helper.succeed();
        });

        // Grid persistence: a pattern left in the grid survives closing the screen (saved to the table
        // BE on close, restored on reopen).
        RCGameTests.test("scrap_crafting_table_grid_persists_across_close", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ServerPlayer player = helper.makeMockServerPlayerInLevel();

            ScrapCraftingStationMenu open = openMenu(helper, player);
            open.getSlot(1).set(new ItemStack(RCItems.SCRAP_METAL.get()));
            open.getSlot(2).set(new ItemStack(RCItems.PLASTIC_SCRAP.get()));
            open.removed(player);   // closes -> saves the grid into the table BE

            ScrapCraftingStationMenu reopened = openMenu(helper, player);   // -> restores from the BE
            helper.assertTrue(reopened.getSlot(1).getItem().is(RCItems.SCRAP_METAL.get()),
                "grid slot 1 must restore scrap metal, got " + reopened.getSlot(1).getItem());
            helper.assertTrue(reopened.getSlot(2).getItem().is(RCItems.PLASTIC_SCRAP.get()),
                "grid slot 2 must restore plastic, got " + reopened.getSlot(2).getItem());
            helper.succeed();
        });

        // Concurrent openers must not wipe the grid: only the first (owner) persists; a second opener
        // gets an empty transient and never writes back. Regression for the 2-player data-loss case.
        RCGameTests.test("scrap_crafting_table_grid_survives_a_second_opener", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ServerPlayer player = helper.makeMockServerPlayerInLevel();

            ScrapCraftingStationMenu owner = openMenu(helper, player);          // checks out the grid
            owner.getSlot(1).set(new ItemStack(RCItems.SCRAP_METAL.get()));
            ScrapCraftingStationMenu second = openMenu(helper, player);          // checked out -> transient
            helper.assertTrue(second.getSlot(1).getItem().isEmpty(),
                "a second opener must not see the checked-out grid");

            owner.removed(player);    // owner saves its grid into the table
            second.removed(player);   // must NOT overwrite the table with its empty grid

            ScrapCraftingStationMenu reopened = openMenu(helper, player);
            helper.assertTrue(reopened.getSlot(1).getItem().is(RCItems.SCRAP_METAL.get()),
                "the owner's grid must survive the second opener's close, got " + reopened.getSlot(1).getItem());
            helper.succeed();
        });

        // Breaking the table drops the stored grid - the pattern is never lost.
        RCGameTests.test("scrap_crafting_table_grid_drops_on_break", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ScrapCraftingStationMenu open = openMenu(helper, player);
            open.getSlot(1).set(new ItemStack(RCItems.SCRAP_METAL.get()));
            open.removed(player);   // grid now lives in the BE

            helper.setBlock(TABLE, Blocks.AIR);   // break -> preRemoveSideEffects drops the grid
            helper.assertItemEntityPresent(RCItems.SCRAP_METAL.get());
            helper.succeed();
        });

        // Negative control: no connected storage and no inventory copy -> the slot stays empty.
        RCGameTests.test("scrap_crafting_table_refill_does_nothing_without_stock", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            menu.getSlot(FIRST_GRID_SLOT).set(new ItemStack(RCItems.SCRAP_METAL.get()));
            Item[] pattern = menu.capturePatternForTest();
            menu.getSlot(FIRST_GRID_SLOT).set(ItemStack.EMPTY);
            menu.refillGridForTest(player, pattern);

            helper.assertTrue(menu.getSlot(FIRST_GRID_SLOT).getItem().isEmpty(),
                "with no bin, barrel or inventory copy the grid slot must stay empty");
            helper.succeed();
        });
    }

    private static ScrapCraftingStationMenu openMenu(GameTestHelper helper, Player player) {
        BlockPos abs = helper.absolutePos(TABLE);
        return new ScrapCraftingStationMenu(1, player.getInventory(), helper.getLevel(), abs);
    }

    private static ScrapBinBlockEntity placeBin(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RCBlocks.SCRAP_BIN.get());
        return (ScrapBinBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    private static int countIn(Player player, Item item) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
