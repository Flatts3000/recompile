package com.flatts.recompile.gametest;

import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import net.minecraft.core.BlockPos;
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
