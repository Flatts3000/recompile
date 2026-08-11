package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.flatts.recompile.content.menu.ScrapPanelInteraction;
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
