package com.flatts.recompile.content.menu;

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
 * The Slag Furnace's menu: vanilla's furnace menu, with one method changed.
 *
 * <p><b>It subclasses {@link AbstractFurnaceMenu}, which the Cupola could not.</b> That class throws on
 * a container of any size but three, and the Cupola needed four for its slag - which cost it vanilla's
 * recipe book and JEI's transfer button (#240). This machine has three slots, so it keeps both, and
 * that is worth more than the symmetry of writing another bespoke menu would have been.
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
