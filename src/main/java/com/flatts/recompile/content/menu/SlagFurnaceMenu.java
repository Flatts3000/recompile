package com.flatts.recompile.content.menu;

import com.flatts.recompile.registry.RCMenus;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.ScreenLayout;
import com.flatts.recompile.registry.RCTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipePropertySet;

/**
 * The Slag Furnace's menu: vanilla's furnace menu, with one method changed.
 *
 * <p><b>It subclasses {@link AbstractFurnaceMenu}, which the Cupola could not.</b> That class throws on
 * a container of any size but three and the Cupola needed four for its slag, so it had to reimplement
 * the slots, {@code quickMoveStack} and the data sync over a bare {@code AbstractContainerMenu}. This
 * machine gets all of that for free, which is the entire reason for the subclass.
 *
 * <p><b>What it does not get is the recipe book or JEI's transfer button</b>, which this javadoc used
 * to claim. Both belong to the SCREEN, not the menu: vanilla's furnace screens build their own
 * recipe-book component and this mod's extends {@code AbstractContainerScreen}, while JEI's furnace
 * transfer handler recognises vanilla's menu classes rather than subclasses of them. #240's account of
 * what the Cupola gave up relative to a VANILLA furnace still stands; what was wrong was reading that
 * as something this machine recovered.
 *
 * <p><b>{@code canSmelt} is overridden onto a tag, and the tag exists because of the client.</b> The
 * inherited version tests a {@link RecipePropertySet}, and vanilla builds those from a fixed set of
 * recipe types - a modded type has none, so the inherited test would answer "no" for slag and
 * shift-clicking the machine's only input would refuse to load it. Testing the recipes directly is not
 * available either: the full recipe map is server-side, and this method runs on both sides for click
 * prediction. A tag is synced, so it answers the same in both places.
 *
 * <p>That makes {@code #recompile:vitrifiable} the thing a pack extends alongside its recipe. Slightly
 * redundant, and the redundancy is the price of shift-click working.
 */
public class SlagFurnaceMenu extends AbstractFurnaceMenu {

    /**
     * Vanilla's furnace geometry, declared here so the geometry sweeps can see it.
     *
     * <p><b>On the MENU, not on the screen</b> - the Cupola's lives here too, and this one did not
     * until review caught it. A layout on a screen class cannot be read by anything server-side:
     * {@code MenuLayoutTests} runs on a dedicated server, and reaching {@code SlagFurnaceScreen.LAYOUT}
     * resolves {@code LayoutScreen} to {@code AbstractContainerScreen}, which is
     * {@code @OnlyIn(Dist.CLIENT)}. It survives a dev run because dev classes are not dist-cleaned, so
     * the failure was waiting for a production server rather than showing up here.
     *
     * <p>The slots are still placed by {@code AbstractFurnaceMenu}'s constructor at exactly these
     * coordinates rather than from this declaration - the one place this screen departs from the
     * framework's "the layout is the single source" rule. The alternative was reimplementing the menu
     * to place its own slots, which is what the Cupola had to do. Better to borrow the menu and let
     * {@code every_menu_slot_comes_from_its_layout} check the two agree.
     */
    public static final ScreenLayout LAYOUT = ScreenLayout.builder(GuiTheme.PANEL_W, GuiTheme.PANEL_H)
        .panel()
        .slot("input", 56, 17)
        .slot("fuel", 56, 53)
        .region("flame", 56, 36, GuiTheme.FLAME_W, GuiTheme.FLAME_H)
        .arrow("cook", 79, 34)
        .slot("result", 116, 35)
        .playerInventory(84)
        .build();


    /**
     * Where the player's inventory starts in this menu, for JEI's transfer handler (#240).
     *
     * <p><b>A constant rather than a literal in the plugin, because getting it wrong is silent.</b>
     * JEI's basic transfer overload takes raw slot indices; hand it an index one off and the "+" button
     * still appears and still moves items, into the wrong slots. Nothing throws, and the plugin is
     * client-only so no server-side test can read a number written there. Declared here, where
     * {@code menu_transfer_ranges_match_the_real_slots} can measure it against the menu it is built
     * from.
     */
    public static final int TRANSFER_INV_START = 3;

    /** The 27 main inventory slots plus the 9 hotbar ones, which is what a transfer may draw from. */
    public static final int TRANSFER_INV_COUNT = 36;

    /** Client factory: a dummy container and data, filled by the sync. */
    public SlagFurnaceMenu(int containerId, Inventory inventory) {
        super(RCMenus.SLAG_FURNACE.get(), RCRecipeTypes.VITRIFYING.get(),
            RecipePropertySet.FURNACE_INPUT, RecipeBookType.FURNACE, containerId, inventory);
    }

    public SlagFurnaceMenu(int containerId, Inventory inventory, Container container,
            ContainerData data) {
        super(RCMenus.SLAG_FURNACE.get(), RCRecipeTypes.VITRIFYING.get(),
            RecipePropertySet.FURNACE_INPUT, RecipeBookType.FURNACE, containerId, inventory,
            container, data);
    }

    /**
     * What this furnace will take, for shift-click and the recipe book.
     *
     * <p>The property set handed to {@code super} is vanilla's smelting one and is deliberately
     * ignored here - there is no modded property set to pass, and passing smelting's would let a player
     * shift-click raw iron into a machine that cannot do anything with it.
     */
    @Override
    protected boolean canSmelt(ItemStack stack) {
        return stack.is(RCTags.VITRIFIABLE);
    }
}
