package com.flatts.recompile.content.menu;

import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;

/**
 * The vanilla 3x3 crafting menu, revalidated against the Scrap Crafting Table.
 *
 * <p>Vanilla {@link CraftingMenu#stillValid} hard-codes {@code Blocks.CRAFTING_TABLE}:
 * it checks that the block at the menu's position is <em>that</em> block and closes the
 * menu otherwise. Opening a plain {@code CraftingMenu} over a scrap crafting table
 * therefore failed validation on the first server tick and the screen shut instantly,
 * which reads in-game as "right-click does nothing". Overriding {@code stillValid} with
 * our own block is the whole fix; every other crafting behaviour stays vanilla.
 *
 * <p>{@code CraftingMenu}'s own {@code access} field is private, so we keep a copy to
 * hand to the inherited range check.
 *
 * <p>This is a server-side concern only. The super constructor registers
 * {@link net.minecraft.world.inventory.MenuType#CRAFTING}, so the client still builds a
 * stock {@code CraftingMenu} plus the vanilla screen (no screen registration needed),
 * and there it holds {@link ContainerLevelAccess#NULL}, whose {@code evaluate} returns
 * the default - so the client never runs this check.
 */
public class ScrapCraftingMenu extends CraftingMenu {

    private final ContainerLevelAccess access;

    public ScrapCraftingMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory,
            ContainerLevelAccess access) {
        super(containerId, inventory, access);
        this.access = access;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, RCBlocks.SCRAP_CRAFTING_TABLE.get());
    }
}
