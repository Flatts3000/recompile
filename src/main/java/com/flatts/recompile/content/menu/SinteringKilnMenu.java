package com.flatts.recompile.content.menu;

import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.ScreenLayout;
import com.flatts.recompile.registry.RCMenus;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.flatts.recompile.registry.RCTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipePropertySet;

/**
 * The Sintering Kiln's menu: vanilla's furnace menu, with one method changed.
 *
 * <p>It subclasses {@link AbstractFurnaceMenu} for the reason the Slag Furnace does - three slots
 * satisfy {@code checkContainerSize(container, 3)}, so the slots, {@code quickMoveStack} and the
 * fuel/progress data sync all come for free. It does <b>not</b> inherit a recipe book or JEI's
 * transfer button; both belong to the SCREEN, and this mod's screens extend
 * {@code AbstractContainerScreen}. That is the corrected account from #240, not the older claim.
 *
 * <p><b>{@code canSmelt} is overridden onto a tag, and the tag exists because of the client.</b> The
 * inherited version tests a {@link RecipePropertySet} and vanilla builds those from a fixed set of
 * recipe types; a modded type has none, so the inherited test answers "no" for a briquette and
 * shift-clicking the machine's only input would silently refuse it. Testing the recipe map directly is
 * not available either, because that map is server-side and this method runs on both sides for click
 * prediction. A tag is synced, so it answers the same in both places - which makes
 * {@code #recompile:sinterable} the thing a pack extends alongside its recipe.
 */
public class SinteringKilnMenu extends AbstractFurnaceMenu {

    /**
     * Vanilla's furnace geometry, declared on the MENU rather than the screen.
     *
     * <p>A layout on a screen class cannot be read by anything server-side: {@code MenuLayoutTests}
     * runs on a dedicated server, and reaching a screen resolves {@code LayoutScreen} to
     * {@code AbstractContainerScreen}, which is {@code @OnlyIn(Dist.CLIENT)}. It survives a dev run
     * because dev classes are not dist-cleaned, so that failure waits for production rather than
     * showing up here. The Slag Furnace's layout lived on its screen until review caught it.
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

    /** Where the player's inventory starts, for JEI's transfer handler. */
    public static final int TRANSFER_INV_START = 3;

    /** The 27 main inventory slots plus the 9 hotbar ones. */
    public static final int TRANSFER_INV_COUNT = 36;

    /** The slot JEI writes a chosen recipe's ingredient into - the machine's input. */
    public static final int TRANSFER_RECIPE_START = 0;

    /** One input, because a kiln fires one compact at a time. */
    public static final int TRANSFER_RECIPE_COUNT = 1;

    /** Client factory: a dummy container and data, filled by the sync. */
    public SinteringKilnMenu(int containerId, Inventory inventory) {
        super(RCMenus.SINTERING_KILN.get(), RCRecipeTypes.SINTERING.get(),
            RecipePropertySet.FURNACE_INPUT, RecipeBookType.FURNACE, containerId, inventory);
    }

    public SinteringKilnMenu(int containerId, Inventory inventory, Container container,
            ContainerData data) {
        super(RCMenus.SINTERING_KILN.get(), RCRecipeTypes.SINTERING.get(),
            RecipePropertySet.FURNACE_INPUT, RecipeBookType.FURNACE, containerId, inventory,
            container, data);
    }

    /**
     * What this kiln will take, for shift-click.
     *
     * <p>The property set handed to {@code super} is vanilla's smelting one and is deliberately
     * ignored: passing it would let a player shift-click raw iron into a machine that can do nothing
     * with it.
     */
    @Override
    protected boolean canSmelt(ItemStack stack) {
        return stack.is(RCTags.SINTERABLE);
    }
}
