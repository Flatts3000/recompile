package com.flatts.recompile.compat.jei;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.compat.TeardownData;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * JEI integration: surfaces the mechanics that are not vanilla recipes and so are
 * otherwise undiscoverable. Loaded only when JEI is present (the API is {@code compileOnly}).
 *
 * <ul>
 *   <li><b>Sorting</b> - what a garbage block / bag / bale gives up, from the pull tables.</li>
 *   <li><b>Cutting</b> - the scrap knife's item transforms (the sealed tin can).</li>
 *   <li><b>Prying</b> - what the prybar breaks out of Bulky Waste.</li>
 *   <li><b>Teardown</b> - what the Recompile Workbench tears a found item into (P1.4).</li>
 *   <li>The Scrap Crafting Table registered as the crafting <b>station</b>, since this world
 *       has no vanilla crafting table for JEI to point at.</li>
 * </ul>
 */
@JeiPlugin
public class RecompileJeiPlugin implements IModPlugin {

    static final RecipeType<SalvageRecipe> SORTING =
        RecipeType.create(Recompile.MOD_ID, "sorting", SalvageRecipe.class);
    static final RecipeType<SalvageRecipe> CUTTING =
        RecipeType.create(Recompile.MOD_ID, "cutting", SalvageRecipe.class);
    static final RecipeType<SalvageRecipe> PRYING =
        RecipeType.create(Recompile.MOD_ID, "prying", SalvageRecipe.class);
    static final RecipeType<SalvageRecipe> TEARDOWN =
        RecipeType.create(Recompile.MOD_ID, "teardown", SalvageRecipe.class);

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "jei");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
            new SalvageCategory(SORTING, Component.translatable("jei.recompile.sorting"),
                gui.createDrawableItemStack(new ItemStack(RCItems.SORTING_TARP.get())), true),
            new SalvageCategory(CUTTING, Component.translatable("jei.recompile.cutting"),
                gui.createDrawableItemStack(new ItemStack(RCItems.SCRAP_KNIFE.get())), false),
            new SalvageCategory(PRYING, Component.translatable("jei.recompile.prying"),
                gui.createDrawableItemStack(new ItemStack(RCItems.PRYBAR.get())), true),
            new SalvageCategory(TEARDOWN, Component.translatable("jei.recompile.teardown"),
                gui.createDrawableItemStack(new ItemStack(RCItems.RECOMPILE_WORKBENCH.get())), true));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<SortingData.Weighted> household = SortingData.outputs(SortingData.HOUSEHOLD);
        List<SortingData.Weighted> bag = SortingData.outputs(SortingData.BAG);
        registration.addRecipes(SORTING, List.of(
            new SalvageRecipe(new ItemStack(RCItems.GARBAGE_BLOCK.get()), household),
            new SalvageRecipe(new ItemStack(RCItems.COMPACTED_BALE.get()), household),
            new SalvageRecipe(new ItemStack(RCItems.TRASH_BAG.get()), bag)));

        registration.addRecipes(CUTTING, List.of(
            new SalvageRecipe(new ItemStack(RCItems.TIN_CAN.get()),
                List.of(new SortingData.Weighted(new ItemStack(RCItems.TIN_CAN_OPEN.get()), 1.0f)))));

        registration.addRecipes(PRYING, List.of(
            new SalvageRecipe(new ItemStack(RCItems.BULKY_WASTE.get()),
                SortingData.outputs(SortingData.BULKY))));

        // Teardown reads the bundled recipe JSON (recipes are not client-synced in 26.1),
        // so the numbers stay single-sourced in the recipe file. The mattress is the one find.
        TeardownData.Entry mattress = TeardownData.read(TeardownData.MATTRESS);
        if (mattress != null) {
            registration.addRecipes(TEARDOWN, List.of(
                new SalvageRecipe(mattress.input(), mattress.outputs())));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // The world's only crafting station - so JEI stops telling players to use a
        // vanilla table they can never make.
        registration.addCraftingStation(RecipeTypes.CRAFTING, RCItems.SCRAP_CRAFTING_TABLE.get());

        registration.addRecipeCatalyst(new ItemStack(RCItems.SORTING_TARP.get()), SORTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.GARBAGE_BLOCK.get()), SORTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.TRASH_BAG.get()), SORTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.COMPACTED_BALE.get()), SORTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.SCRAP_KNIFE.get()), CUTTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.PRYBAR.get()), PRYING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.RECOMPILE_WORKBENCH.get()), TEARDOWN);
    }
}
