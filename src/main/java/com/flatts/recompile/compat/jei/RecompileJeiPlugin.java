package com.flatts.recompile.compat.jei;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.compat.TeardownData;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import com.flatts.recompile.client.RCSyncedRecipes;
import com.flatts.recompile.content.block.entity.BurnBarrelBlockEntity;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
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
    /**
     * Torch cutting is its OWN category, not a second entry under CUTTING. JEI catalysts attach to a
     * category, never to a single recipe, so sharing one would advertise the scrap knife as a way to cut
     * steel and the torch as a way to open a tin can. Two tools, two categories.
     */
    /**
     * What the Burn Barrel will actually burn. Its own category because JEI catalysts attach to a CATEGORY,
     * never to a single recipe - so listing the barrel against {@code RecipeTypes.SMELTING} told players
     * they could smelt iron or glass in a drum fire that refuses both.
     */
    static final RecipeType<SalvageRecipe> BURNING =
        RecipeType.create(Recompile.MOD_ID, "burning", SalvageRecipe.class);
    static final RecipeType<SalvageRecipe> TORCH_CUTTING =
        RecipeType.create(Recompile.MOD_ID, "torch_cutting", SalvageRecipe.class);
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
            new SalvageCategory(BURNING, Component.translatable("jei.recompile.burning"),
                gui.createDrawableItemStack(new ItemStack(RCItems.BURN_BARREL.get())), false),
            new SalvageCategory(TORCH_CUTTING, Component.translatable("jei.recompile.torch_cutting"),
                gui.createDrawableItemStack(new ItemStack(RCItems.CUTTING_TORCH.get())), true),
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

        // A Steel Offcut has a smelting recipe, so JEI could already say what it BECOMES - but nothing
        // said where it comes FROM, because block drops are invisible to JEI. An item with a use and no
        // source reads like a bug.
        // The barrel's category is built from the SYNCED smelting recipes, filtered by the same predicate
        // the block itself uses - so it lists exactly what it burns and cannot drift from the rule. Empty
        // before a world is joined, which never happens in practice: JEI starts on world load.
        List<SalvageRecipe> burnable = new ArrayList<>();
        RecipeMap synced = RCSyncedRecipes.get();
        if (synced != null) {
            for (RecipeHolder<SmeltingRecipe> holder : synced.byType(net.minecraft.world.item.crafting.RecipeType.SMELTING)) {
                SmeltingRecipe recipe = holder.value();
                recipe.input().items().forEach(item -> {
                    ItemStack in = new ItemStack(item);
                    if (!BurnBarrelBlockEntity.burns(in)) {
                        return;
                    }
                    ItemStack out = recipe.assemble(new SingleRecipeInput(in));
                    if (!out.isEmpty()) {
                        burnable.add(new SalvageRecipe(in,
                            List.of(new SortingData.Weighted(out, 1.0f))));
                    }
                });
            }
        }
        registration.addRecipes(BURNING, burnable);

        registration.addRecipes(TORCH_CUTTING, List.of(
            new SalvageRecipe(new ItemStack(RCItems.STEEL_I_BEAM.get()),
                SortingData.outputs(SortingData.STEEL_BEAM))));

        registration.addRecipes(PRYING, List.of(
            new SalvageRecipe(new ItemStack(RCItems.BULKY_WASTE.get()),
                SortingData.outputs(SortingData.BULKY))));

        // Teardown reads the bundled recipe JSON (recipes are not client-synced in 26.1), so the
        // numbers stay single-sourced in the recipe file. Iterating every entry means a new find
        // shows up here for free, rather than needing this list edited too.
        List<SalvageRecipe> teardowns = new ArrayList<>();
        for (TeardownData.Entry entry : TeardownData.all()) {
            teardowns.add(new SalvageRecipe(entry.input(), entry.outputs()));
        }
        if (!teardowns.isEmpty()) {
            registration.addRecipes(TEARDOWN, teardowns);
        }

        // Machines only, not their parts. A crafted core says nothing about the tower it needs, and
        // JEI is where a player goes looking. The parts already have recipes here, and the appliance
        // already has a teardown entry, so a panel on those would only restate what JEI shows.
        // The Burn Barrel is a catalyst for SMELTING, but since it went refuse-only that is only true of
        // SOME smelting recipes - JEI has no way to attach a catalyst to a subset, so it would otherwise
        // tell a player they can smelt iron or glass in a barrel that refuses both. The panel is where
        // that gets said. Same for the two mechanics no recipe expresses: the torch's charge, and the
        // Cupola being the machine that lifts the barrel's restriction.
        info(registration, RCItems.BURN_BARREL.get(), "burn_barrel");
        info(registration, RCItems.CUPOLA_FURNACE.get(), "cupola_furnace");
        info(registration, RCItems.CUTTING_TORCH.get(), "cutting_torch");
        info(registration, RCItems.STEEL_OFFCUT.get(), "steel_offcut");

        info(registration, RCItems.GRASS_SPREADER.get(), "grass_spreader");
        info(registration, RCItems.RAIN_COLLECTOR.get(), "rain_collector");
        info(registration, RCItems.COMPOST_HEAP.get(), "compost_heap");
        info(registration, RCItems.FERTILIZER.get(), "fertilizer");
    }

    private static void info(IRecipeRegistration registration, net.minecraft.world.level.ItemLike item,
            String key) {
        registration.addIngredientInfo(item, Component.translatable("jei.recompile.info." + key));
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
        registration.addRecipeCatalyst(new ItemStack(RCItems.CUTTING_TORCH.get()), TORCH_CUTTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.PRYBAR.get()), PRYING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.RECOMPILE_WORKBENCH.get()), TEARDOWN);
        // The Burn Barrel is NOT a general smelting station - it burns refuse only, so it is the catalyst
        // for its own category, which lists exactly what it takes.
        registration.addRecipeCatalyst(new ItemStack(RCItems.BURN_BARREL.get()), BURNING);
        // The Cupola is the other furnace - and the unrestricted one, so it is where JEI should send
        // a player looking to smelt anything the barrel refuses.
        registration.addRecipeCatalyst(new ItemStack(RCItems.CUPOLA_FURNACE.get()), RecipeTypes.SMELTING);
    }
}
