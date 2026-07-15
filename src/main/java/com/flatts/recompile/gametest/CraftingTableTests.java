package com.flatts.recompile.gametest;

import com.flatts.recompile.content.menu.ScrapCraftingMenu;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * GameTests for the Scrap Crafting Table's parity with the vanilla crafting table.
 */
final class CraftingTableTests {

    private CraftingTableTests() {
    }

    static void register() {
        // Regression: the table opened a plain vanilla CraftingMenu, whose stillValid
        // hard-codes Blocks.CRAFTING_TABLE. It failed on the first tick over a scrap
        // table, so the menu shut instantly and right-clicking looked inert. A compile
        // cannot see that, and neither can a test that only places the block - so
        // assert the menu validates over the block it was opened on.
        RCGameTests.test("scrap_crafting_table_menu_stays_open", 20, helper -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, RCBlocks.SCRAP_CRAFTING_TABLE.get());

            Player player = helper.makeMockPlayer(GameType.CREATIVE);
            Vec3 standing = helper.absoluteVec(pos.above().getCenter());
            player.snapTo(standing.x, standing.y, standing.z);

            ContainerLevelAccess access =
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos));
            ScrapCraftingMenu menu = new ScrapCraftingMenu(1, player.getInventory(), access);

            helper.assertTrue(menu.stillValid(player),
                "crafting menu must stay valid over a scrap crafting table");

            // And it must still close once the table is gone, or it would never shut.
            helper.setBlock(pos, Blocks.AIR);
            helper.assertFalse(menu.stillValid(player),
                "crafting menu must close once the table is gone");
            helper.succeed();
        });
    }
}
