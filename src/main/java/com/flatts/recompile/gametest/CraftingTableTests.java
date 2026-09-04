package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.flatts.recompile.content.menu.ScrapPanelInteraction;
import com.flatts.recompile.network.FillGridPayload;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    // ...then the player's 36: 10..36 is the main inventory and 37..45 the hotbar. The menu's own
    // quick-move is written against exactly these boundaries, so they are named here rather than typed
    // into each test - a shuffle test that guesses the split proves nothing about the code's split.
    private static final int FIRST_INV_SLOT = 10;
    private static final int FIRST_HOTBAR_SLOT = 37;
    private static final int INV_END = 46;

    private CraftingTableTests() {
    }

    static void register() {
        // The bed gate (blueprint POC): wool must no longer make a bed in ANY colour. Sheep are in the
        // herbivore bait list, so wool is reachable at rung 5 - if even one of the sixteen colour
        // recipes survives, the whole Clean Mattress path is decorative. Sixteen files is exactly the
        // kind of surface where fifteen get done.
        RCGameTests.test("wool_can_no_longer_make_a_bed", 20, helper -> {
            var recipeMap = helper.getLevel().getServer().getRecipeManager().recipeMap();
            List<String> alive = new ArrayList<>();
            int checked = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                if (!id.getPath().endsWith("_wool")) {
                    continue;
                }
                checked++;
                var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 2, java.util.List.of(
                    new ItemStack(item), new ItemStack(item), new ItemStack(item),
                    new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS),
                    new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS),
                    new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS)));
                recipeMap.getRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,
                        input, helper.getLevel())
                    .forEach(h -> alive.add(id.getPath() + " -> " + h.id()));
            }
            helper.assertTrue(checked >= 16,
                "only " + checked + " wools were swept - discovery is broken, so this would pass "
                    + "against a surviving recipe");
            helper.assertTrue(alive.isEmpty(),
                "wool still crafts a bed, so the Clean Mattress gate leaks: " + alive);
            helper.succeed();
        });

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

        // ONE SHIFT-CLICK IS ONE BATCH, NOT THE WHOLE NETWORK (#127).
        //
        // This test used to assert the opposite: that a single shift-click drained a 40-item bin into
        // 11 crafts. That was the shipped behaviour and it was deliberate, which is why it had a test -
        // but playtest found it as a bug, twice, and the owner's call is that shift-click should behave
        // like a crafting station rather than like AE2. Nothing in this mod un-crafts, so one keypress
        // spending a whole sorted wall is not a convenience.
        //
        // Driven through `clicked` rather than quickMoveStack, because the fix lives in the difference
        // between them: the refill is once per CLICK now, and calling quickMoveStack directly would
        // exercise a path the player never takes.
        //
        // scrap_plating is 4 scrap_metal -> 1, a single-material recipe, so the math is exact.
        RCGameTests.test("one_shift_click_crafts_one_batch_not_the_whole_network", 40, helper -> {
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

            // One shift-click on the result, exactly as a player does it.
            menu.clicked(0, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);

            // The grid held one craft's worth, so one craft came out - not eleven. The bin is the
            // point: 40 scrap metal sat there the whole time and the click did not touch it.
            int made = countIn(player, RCItems.SCRAP_PLATING.get());
            helper.assertTrue(made == 1,
                "one shift-click should craft one batch, got " + made + ". A click that keeps going "
                    + "until the network is empty cannot be undone");
            helper.assertTrue(bin.amount() == 36,
                "the bin should have paid for exactly the ONE refill, leaving 36, has " + bin.amount());

            // And the convenience survives: the grid is stocked again, so the next click works without
            // the player restocking by hand. That is the half that makes this a fix and not a removal.
            for (int gridSlot : new int[] {1, 2, 4, 5}) {
                helper.assertTrue(menu.getSlot(gridSlot).getItem().is(RCItems.SCRAP_METAL.get()),
                    "grid slot " + gridSlot + " should have been restocked from the bin after the "
                        + "click, holds " + menu.getSlot(gridSlot).getItem());
            }
            helper.succeed();
        });

        // Panel withdraw: clicking a material pulls a stack of it out of the network into the player.
        // Driven through clickMenuButton (the button id is the item's registry id), as the screen does.
        // Each click mode pulls its own quantity (issue #86). ScrapPanelInteractionTest covers the
        // arithmetic; this covers the wiring - that the mode survives the button encoding, reaches the
        // bin, and moves the real item count. The two halves fail differently and both have to hold.
        RCGameTests.test("scrap_crafting_table_panel_withdraws_per_click_mode", 20, helper -> {
            record Case(ScrapPanelInteraction.Mode mode, int expected) { }
            for (Case c : new Case[] {
                    new Case(ScrapPanelInteraction.Mode.ONE, 1),
                    new Case(ScrapPanelInteraction.Mode.STACK, 64),
                    new Case(ScrapPanelInteraction.Mode.HALF, 32)}) {
                // Clear to AIR first. Re-placing the same block over an existing one keeps its
                // BlockEntity, so without this the bin's contents accumulate across the three cases and
                // every count after the first is measured against the wrong starting stock.
                BlockPos binPos = new BlockPos(2, 1, 1);
                helper.setBlock(binPos, Blocks.AIR);
                helper.setBlock(TABLE, Blocks.AIR);

                helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
                ScrapBinBlockEntity bin = placeBin(helper, binPos);
                bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 100));

                ServerPlayer player = helper.makeMockServerPlayerInLevel();
                ScrapCraftingStationMenu menu = openMenu(helper, player);

                int button = ScrapPanelInteraction.encode(
                    BuiltInRegistries.ITEM.getId(RCItems.SCRAP_METAL.get()), c.mode());
                boolean handled = menu.clickMenuButton(player, button);

                helper.assertTrue(handled, c.mode() + " on a stocked material must withdraw");
                int got = countIn(player, RCItems.SCRAP_METAL.get());
                helper.assertTrue(got == c.expected(),
                    c.mode() + " should pull " + c.expected() + ", player got " + got);
                helper.assertTrue(bin.amount() == 100 - c.expected(),
                    c.mode() + " should leave " + (100 - c.expected()) + " in the bin, has " + bin.amount());
                player.discard();
            }
            helper.succeed();
        });

        // A button whose mode ordinal is not a real mode must be a no-op. It comes off the wire, so a
        // modified client can send anything, and "assume ONE" would let it withdraw on a malformed
        // packet naming any item it liked.
        RCGameTests.test("scrap_crafting_table_panel_rejects_a_bogus_click_mode", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 100));

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            int bogus = (99 << 24) | BuiltInRegistries.ITEM.getId(RCItems.SCRAP_METAL.get());
            boolean handled = menu.clickMenuButton(player, bogus);

            helper.assertFalse(handled, "an unknown click mode must not withdraw");
            helper.assertTrue(countIn(player, RCItems.SCRAP_METAL.get()) == 0, "nothing should be given");
            helper.assertTrue(bin.amount() == 100, "the bin must be untouched, has " + bin.amount());
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

    /**
     * Shift-click and the JEI transfer button: the two ways items move through this menu without the
     * player picking them up.
     *
     * <p>This is the mod's ONE bespoke crafting menu. It reimplements crafting over a bare
     * {@code AbstractContainerMenu} because vanilla {@code CraftingMenu}'s constructor hard-locks itself
     * to {@code MenuType.CRAFTING}, so none of vanilla's own testing covers a line of it - and
     * quick-move is exactly where a hand-written menu voids or duplicates a stack. Every test here
     * counts the items in the menu before and after, because "the item ended up in the right slot" and
     * "no items were created or destroyed" are different claims and only the second one is the bug.
     *
     * <p><b>Slot 0 is deliberately excluded from every count.</b> It is the result PREVIEW, recomputed
     * from the grid on every change and owned by nobody; counting it would report a craft as items
     * appearing from nowhere.
     */
    static void registerQuickMove() {
        // Shift-clicking the grid empties it back into the inventory and loses nothing on the way. A
        // quick-move that reports a move it did not make leaves the item in the grid AND in the
        // inventory; one that returns early after emptying the slot deletes it outright. The slot
        // assertions cannot tell those apart - the count can.
        RCGameTests.test("shift_clicking_the_grid_empties_it_into_the_inventory", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            for (int i = 0; i < 9; i++) {
                menu.getSlot(FIRST_GRID_SLOT + i).set(new ItemStack(RCItems.SCRAP_METAL.get(), 5));
            }
            int before = realItemsIn(menu);
            helper.assertTrue(before == 45, "the fixture must put 45 items in the grid, put " + before);

            for (int i = 0; i < 9; i++) {
                menu.quickMoveStack(player, FIRST_GRID_SLOT + i);
            }

            for (int i = 0; i < 9; i++) {
                helper.assertTrue(menu.getSlot(FIRST_GRID_SLOT + i).getItem().isEmpty(),
                    "grid slot " + (FIRST_GRID_SLOT + i) + " should have been emptied, holds "
                        + menu.getSlot(FIRST_GRID_SLOT + i).getItem());
            }
            helper.assertTrue(countIn(player, RCItems.SCRAP_METAL.get()) == 45,
                "all 45 must arrive in the inventory, got " + countIn(player, RCItems.SCRAP_METAL.get()));
            helper.assertTrue(realItemsIn(menu) == before,
                "the menu held " + before + " items and now holds " + realItemsIn(menu)
                    + " - a quick-move must move items, never mint or destroy them");
            helper.succeed();
        });

        // The other direction. Shift-clicking from the inventory stocks the grid, and the same count has
        // to come out the other side: the grid and the inventory are two containers, and this menu wires
        // them together by hand.
        RCGameTests.test("shift_clicking_from_the_inventory_stocks_the_grid", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            menu.getSlot(FIRST_INV_SLOT).set(new ItemStack(RCItems.SCRAP_METAL.get(), 12));
            int before = realItemsIn(menu);

            menu.quickMoveStack(player, FIRST_INV_SLOT);

            helper.assertTrue(menu.getSlot(FIRST_GRID_SLOT).getItem().is(RCItems.SCRAP_METAL.get()),
                "the stack must land in the first free grid slot, holds "
                    + menu.getSlot(FIRST_GRID_SLOT).getItem());
            helper.assertTrue(menu.getSlot(FIRST_GRID_SLOT).getItem().getCount() == 12,
                "the whole stack moves, grid slot holds "
                    + menu.getSlot(FIRST_GRID_SLOT).getItem().getCount());
            helper.assertTrue(menu.getSlot(FIRST_INV_SLOT).getItem().isEmpty(),
                "the inventory slot must be emptied, holds " + menu.getSlot(FIRST_INV_SLOT).getItem());
            helper.assertTrue(realItemsIn(menu) == before,
                "the menu held " + before + " items and now holds " + realItemsIn(menu));
            helper.succeed();
        });

        // With the grid full, shift-clicking shuffles between the main inventory and the hotbar - the
        // vanilla behaviour, reimplemented here by hand as two index ranges. Get either bound wrong and
        // the click silently does nothing, which is how a player concludes the table is broken; get the
        // ranges overlapping and an item is moved onto itself.
        RCGameTests.test("shift_clicking_a_full_grid_shuffles_hotbar_and_inventory", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            // Stone, not scrap metal: the grid must be full AND unable to accept what is shift-clicked,
            // or the move goes into the grid and this proves nothing.
            for (int i = 0; i < 9; i++) {
                menu.getSlot(FIRST_GRID_SLOT + i).set(new ItemStack(Items.STONE, 1));
            }
            int before = realItemsIn(menu);

            // Main inventory -> hotbar.
            menu.getSlot(FIRST_INV_SLOT).set(new ItemStack(RCItems.SCRAP_METAL.get(), 7));
            menu.quickMoveStack(player, FIRST_INV_SLOT);
            helper.assertTrue(menu.getSlot(FIRST_INV_SLOT).getItem().isEmpty(),
                "a main-inventory stack must leave its slot when the grid cannot take it");
            helper.assertTrue(countIn(menu, FIRST_HOTBAR_SLOT, INV_END, RCItems.SCRAP_METAL.get()) == 7,
                "it must land in the hotbar, hotbar holds "
                    + countIn(menu, FIRST_HOTBAR_SLOT, INV_END, RCItems.SCRAP_METAL.get()));

            // Hotbar -> main inventory, the mirror branch.
            menu.getSlot(INV_END - 1).set(new ItemStack(RCItems.PLASTIC_SCRAP.get(), 3));
            menu.quickMoveStack(player, INV_END - 1);
            helper.assertTrue(menu.getSlot(INV_END - 1).getItem().isEmpty(),
                "a hotbar stack must leave its slot when the grid cannot take it");
            helper.assertTrue(
                countIn(menu, FIRST_INV_SLOT, FIRST_HOTBAR_SLOT, RCItems.PLASTIC_SCRAP.get()) == 3,
                "it must land in the main inventory, which holds "
                    + countIn(menu, FIRST_INV_SLOT, FIRST_HOTBAR_SLOT, RCItems.PLASTIC_SCRAP.get()));

            helper.assertTrue(realItemsIn(menu) == before + 10,
                "the shuffle must move exactly the ten items it was given, menu holds "
                    + realItemsIn(menu) + " against an expected " + (before + 10));
            helper.succeed();
        });

        // Shift-crafting consumes the grid exactly ONCE per craft. The result slot is the one place in
        // this menu where taking an item also mutates a different container, so an off-by-one there
        // either leaves the inputs behind (a free craft, repeatable forever) or takes two of each.
        RCGameTests.test("shift_crafting_consumes_the_grid_exactly_once", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());   // no network, so no refill
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            // scrap_plating is a shaped 2x2 of scrap metal, so the arithmetic is exact: 4 in, 1 out.
            for (int gridSlot : new int[] {1, 2, 4, 5}) {
                menu.getSlot(gridSlot).set(new ItemStack(RCItems.SCRAP_METAL.get(), 1));
            }
            helper.assertTrue(menu.getSlot(ScrapCraftingStationMenu.RESULT_SLOT).getItem()
                    .is(RCItems.SCRAP_PLATING.get()),
                "the grid must show scrap plating or the quick-move below returns immediately and this "
                    + "test proves nothing, result slot holds "
                    + menu.getSlot(ScrapCraftingStationMenu.RESULT_SLOT).getItem());

            menu.quickMoveStack(player, ScrapCraftingStationMenu.RESULT_SLOT);

            helper.assertTrue(countIn(player, RCItems.SCRAP_PLATING.get()) == 1,
                "one craft's worth of inputs must make exactly one plating, got "
                    + countIn(player, RCItems.SCRAP_PLATING.get()));
            helper.assertTrue(countIn(player, RCItems.SCRAP_METAL.get()) == 0,
                "the inputs are spent, not handed back, player holds "
                    + countIn(player, RCItems.SCRAP_METAL.get()));
            for (int gridSlot : new int[] {1, 2, 4, 5}) {
                helper.assertTrue(menu.getSlot(gridSlot).getItem().isEmpty(),
                    "grid slot " + gridSlot + " must be spent by the craft, holds "
                        + menu.getSlot(gridSlot).getItem());
            }
            helper.succeed();
        });

        // ...and with nowhere to put the result the craft is REFUSED rather than half-done. This is the
        // void: consume the grid, fail to place the output, and four scrap metal are gone with nothing
        // to show for them. A full inventory is not an edge case at a crafting station.
        RCGameTests.test("a_full_inventory_refuses_the_craft_instead_of_voiding_it", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            for (int gridSlot : new int[] {1, 2, 4, 5}) {
                menu.getSlot(gridSlot).set(new ItemStack(RCItems.SCRAP_METAL.get(), 1));
            }
            helper.assertTrue(menu.getSlot(ScrapCraftingStationMenu.RESULT_SLOT).getItem()
                    .is(RCItems.SCRAP_PLATING.get()),
                "the grid must show a result before the inventory is filled, or the refusal below is "
                    + "just an empty result slot");
            // Stone, at max stack, in every one of the 36 player slots: nothing to merge with and
            // nowhere free, so the move genuinely cannot be made.
            for (int slot = FIRST_INV_SLOT; slot < INV_END; slot++) {
                menu.getSlot(slot).set(new ItemStack(Items.STONE, 64));
            }

            ItemStack moved = menu.quickMoveStack(player, ScrapCraftingStationMenu.RESULT_SLOT);

            helper.assertTrue(moved.isEmpty(),
                "a craft that cannot be delivered must report nothing moved, got " + moved);
            helper.assertTrue(countIn(player, RCItems.SCRAP_PLATING.get()) == 0,
                "no plating may reach a full inventory");
            for (int gridSlot : new int[] {1, 2, 4, 5}) {
                helper.assertTrue(menu.getSlot(gridSlot).getItem().is(RCItems.SCRAP_METAL.get()),
                    "the grid must be left untouched by a refused craft, slot " + gridSlot + " holds "
                        + menu.getSlot(gridSlot).getItem());
            }
            helper.succeed();
        });

        // Double-click-to-collect must never reach the result slot. Vanilla's collect sweeps every slot
        // holding the item on the cursor, and the result slot always holds one more of whatever the grid
        // makes - so without this guard a double-click after a craft drags batch after batch out of the
        // grid with no click of its own and no bound, which is #127's failure by another door.
        RCGameTests.test("double_click_collect_never_reaches_the_result_slot", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            ScrapCraftingStationMenu menu = openMenu(helper, player);
            for (int gridSlot : new int[] {1, 2, 4, 5}) {
                menu.getSlot(gridSlot).set(new ItemStack(RCItems.SCRAP_METAL.get(), 1));
            }

            ItemStack plating = new ItemStack(RCItems.SCRAP_PLATING.get());
            helper.assertFalse(
                menu.canTakeItemForPickAll(plating, menu.getSlot(ScrapCraftingStationMenu.RESULT_SLOT)),
                "collect-all must skip the result slot");
            // The opposite, so this cannot pass on a method that refuses everything: ordinary slots are
            // still collectable, which is the behaviour players expect from every other container.
            helper.assertTrue(
                menu.canTakeItemForPickAll(new ItemStack(RCItems.SCRAP_METAL.get()),
                    menu.getSlot(FIRST_GRID_SLOT)),
                "a grid slot must still be collectable");
            helper.assertTrue(
                menu.canTakeItemForPickAll(new ItemStack(RCItems.SCRAP_METAL.get()),
                    menu.getSlot(FIRST_INV_SLOT)),
                "an inventory slot must still be collectable");
            helper.succeed();
        });

        // JEI's transfer button, server side. It sources the player's own inventory BEFORE the network,
        // and that ordering is the whole point: taking from a shared barrel while the crafter is already
        // carrying the item quietly redistributes other people's storage on a server.
        RCGameTests.test("the_transfer_button_sources_the_player_before_the_network", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 10));

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            standAtTable(helper, player);
            player.getInventory().add(new ItemStack(RCItems.SCRAP_METAL.get(), 2));
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            menu.fillGrid(gridRequest(RCItems.SCRAP_METAL.get(), 0, 1, 3, 4));

            for (int gridSlot : new int[] {1, 2, 4, 5}) {
                helper.assertTrue(menu.getSlot(gridSlot).getItem().is(RCItems.SCRAP_METAL.get()),
                    "the transfer must fill grid slot " + gridSlot + ", holds "
                        + menu.getSlot(gridSlot).getItem());
                helper.assertTrue(menu.getSlot(gridSlot).getItem().getCount() == 1,
                    "one per slot, got " + menu.getSlot(gridSlot).getItem().getCount());
            }
            helper.assertTrue(countIn(player, RCItems.SCRAP_METAL.get()) == 0,
                "the player's own two must be spent first, they still hold "
                    + countIn(player, RCItems.SCRAP_METAL.get()));
            helper.assertTrue(bin.amount() == 8,
                "the bin covers only the remaining two, has " + bin.amount());
            helper.succeed();
        });

        // A second transfer returns whatever the grid already held before it lays out the new recipe.
        // Without that, transfer #2 stacks its ingredients on top of transfer #1 and produces a grid
        // matching neither recipe - and the ingredients it could not place are simply gone.
        RCGameTests.test("the_transfer_button_returns_what_the_grid_already_held", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            standAtTable(helper, player);
            player.getInventory().add(new ItemStack(RCItems.SCRAP_METAL.get(), 1));
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            menu.getSlot(FIRST_GRID_SLOT).set(new ItemStack(RCItems.PLASTIC_SCRAP.get(), 4));

            menu.fillGrid(gridRequest(RCItems.SCRAP_METAL.get(), 0));

            helper.assertTrue(menu.getSlot(FIRST_GRID_SLOT).getItem().is(RCItems.SCRAP_METAL.get()),
                "the requested item must take the slot, holds " + menu.getSlot(FIRST_GRID_SLOT).getItem());
            helper.assertTrue(countIn(player, RCItems.PLASTIC_SCRAP.get()) == 4,
                "all four plastic must come back to the player, they hold "
                    + countIn(player, RCItems.PLASTIC_SCRAP.get()));
            helper.succeed();
        });

        // Both guards on the transfer, which come off the wire and so must land on a no-op rather than
        // on a best effort. A short payload would index past the grid; an out-of-reach menu is one
        // vanilla has not closed yet, and fillGrid reaches into real blocks in the world - a player who
        // walked away must not keep draining the bins they left behind.
        RCGameTests.test("the_transfer_button_refuses_a_malformed_or_out_of_reach_request", 20, helper -> {
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());
            ScrapBinBlockEntity bin = placeBin(helper, new BlockPos(2, 1, 1));
            bin.deposit(new ItemStack(RCItems.SCRAP_METAL.get(), 10));

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            standAtTable(helper, player);
            ScrapCraftingStationMenu menu = openMenu(helper, player);

            menu.fillGrid(List.of(BuiltInRegistries.ITEM.getId(RCItems.SCRAP_METAL.get())));   // 1, not 9
            helper.assertTrue(menu.getSlot(FIRST_GRID_SLOT).getItem().isEmpty(),
                "a payload that is not a full grid must be ignored, grid slot holds "
                    + menu.getSlot(FIRST_GRID_SLOT).getItem());
            helper.assertTrue(bin.amount() == 10, "and must not touch the bin, which has " + bin.amount());

            // Well-formed, but the player has walked out of range of the table.
            Vec3 away = helper.absoluteVec(TABLE.getCenter()).add(12.0, 0.0, 0.0);
            player.snapTo(away.x, away.y, away.z);
            menu.fillGrid(gridRequest(RCItems.SCRAP_METAL.get(), 0));

            helper.assertTrue(menu.getSlot(FIRST_GRID_SLOT).getItem().isEmpty(),
                "an out-of-reach menu must not fill, grid slot holds "
                    + menu.getSlot(FIRST_GRID_SLOT).getItem());
            helper.assertTrue(bin.amount() == 10,
                "and must not drain the bin the player walked away from, which has " + bin.amount());
            helper.succeed();
        });
    }

    /** Nine slot ids for {@link ScrapCraftingStationMenu#fillGrid}, {@code item} in the named slots. */
    private static List<Integer> gridRequest(Item item, int... slots) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < FillGridPayload.SLOTS; i++) {
            ids.add(FillGridPayload.EMPTY);
        }
        for (int slot : slots) {
            ids.set(slot, BuiltInRegistries.ITEM.getId(item));
        }
        return ids;
    }

    /** Put the player on top of the table so {@code stillValid} holds. */
    private static void standAtTable(GameTestHelper helper, ServerPlayer player) {
        Vec3 standing = helper.absoluteVec(TABLE.above().getCenter());
        player.snapTo(standing.x, standing.y, standing.z);
    }

    /**
     * Every item the menu really owns: the grid plus the player's inventory.
     *
     * <p>Slot 0 is left out on purpose. It is the result preview, written by the recipe lookup and
     * owned by nobody, so counting it turns every craft into items appearing from thin air.
     */
    private static int realItemsIn(ScrapCraftingStationMenu menu) {
        int total = 0;
        for (int slot = FIRST_GRID_SLOT; slot < INV_END; slot++) {
            total += menu.getSlot(slot).getItem().getCount();
        }
        return total;
    }

    /** How many of {@code item} sit in the menu slots {@code [from, to)}. */
    private static int countIn(ScrapCraftingStationMenu menu, int from, int to, Item item) {
        int total = 0;
        for (int slot = from; slot < to; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Register the network-reporting tests. Split out only to keep {@code register} readable.
     */
    static void registerNetworkReporting() {
        /*
         * WHAT THE NETWORK REPORTS IS NOT ALLOWED TO BE A SHORT LIST.
         *
         * The snapshot the client gets was capped at 18 distinct materials because "the panel shows a
         * few rows" - and two things read it. The shelf scrolls, so the cap hid content the player was
         * meant to reach; and ScrapTableTransfer decides from it whether a recipe's ingredients are
         * available, so a barrel holding 19 Rebar answered "Not in your inventory or any connected
         * storage" because Rebar was the 25th distinct item (playtest, 2026-08-11).
         *
         * Driven with TWO barrels and more distinct items than any cap anyone would pick by eye,
         * because "what if I connect several barrels" is the question that makes a cap indefensible.
         */
        RCGameTests.test("the_network_reports_every_material_across_every_barrel", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            helper.setBlock(TABLE, RCBlocks.SCRAP_CRAFTING_TABLE.get());

            // Two barrels, both touching the table, so this covers aggregation as well as the cap.
            BlockPos[] barrelPositions = {TABLE.east(), TABLE.west()};
            List<Container> barrels = new ArrayList<>();
            for (BlockPos pos : barrelPositions) {
                helper.setBlock(pos, RCBlocks.SCRAP_BARREL.get());
                barrels.add((Container) helper.getLevel().getBlockEntity(helper.absolutePos(pos)));
            }

            // Fill them with distinct items, well past any plausible display cap. Taken from the item
            // registry so this does not need a hand-written list of forty ids.
            List<Item> distinct = new ArrayList<>();
            for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                if (item != net.minecraft.world.item.Items.AIR) {
                    distinct.add(item);
                }
                if (distinct.size() >= 40) {
                    break;
                }
            }
            for (int i = 0; i < distinct.size(); i++) {
                Container barrel = barrels.get(i % barrels.size());
                barrel.setItem(i / barrels.size(), new ItemStack(distinct.get(i), 1));
            }

            ScrapCraftingStationMenu menu = openMenu(helper, player);
            var reported = menu.contentsForTest().materials().stream()
                .map(m -> m.item()).collect(java.util.stream.Collectors.toSet());

            List<String> missing = new ArrayList<>();
            for (Item item : distinct) {
                if (!reported.contains(item)) {
                    missing.add(String.valueOf(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)));
                }
            }
            helper.assertTrue(missing.isEmpty(),
                "the network holds " + distinct.size() + " distinct materials across two barrels and "
                    + "reported " + reported.size() + ". These were dropped, so the shelf cannot show "
                    + "them and JEI transfer will call them missing: " + missing);
            player.discard();
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
