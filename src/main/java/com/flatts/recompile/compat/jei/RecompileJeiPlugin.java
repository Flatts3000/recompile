package com.flatts.recompile.compat.jei;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.compat.SortingData;
import com.flatts.recompile.compat.TeardownData;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import com.flatts.recompile.client.RCSyncedRecipes;
import com.flatts.recompile.content.block.entity.BurnBarrelBlockEntity;
import com.flatts.recompile.content.block.entity.HydroponicsBayBlockEntity;
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

    /**
     * Vanilla's smelting recipe type, spelled out because {@code RecipeType} in this file is JEI's - the two
     * share a simple name and cannot both be imported.
     */
    private static final net.minecraft.world.item.crafting.RecipeType<SmeltingRecipe> VANILLA_SMELTING =
        net.minecraft.world.item.crafting.RecipeType.SMELTING;
    static final RecipeType<SalvageRecipe> TORCH_CUTTING =
        RecipeType.create(Recompile.MOD_ID, "torch_cutting", SalvageRecipe.class);
    static final RecipeType<SalvageRecipe> PRYING =
        RecipeType.create(Recompile.MOD_ID, "prying", SalvageRecipe.class);
    static final RecipeType<SalvageRecipe> TEARDOWN =
        RecipeType.create(Recompile.MOD_ID, "teardown", SalvageRecipe.class);
    /**
     * The Hydroponics Bay. Its own category because nothing else in the game answers either of the two
     * questions it raises: which plants the Unknown Seedling can turn out to be, and which plants the bay
     * will grow at all. Both are data - a loot table and an item tag - so neither is a recipe JEI could
     * find on its own, and the four that matter most (cane, bamboo, cactus, berries) have no other source
     * in the world. Without this the bay is a machine with no discoverable inputs.
     */
    /**
     * Blueprint crafting (#95). Its own category for the reason every category here has one: JEI
     * catalysts attach to a CATEGORY, never to a single recipe, so listing these under CRAFTING would
     * advertise the Scrap Crafting Table as able to make a Clean Mattress with no blueprint at all -
     * which is the one thing the whole system exists to prevent.
     */
    /**
     * The mod's special crafting recipes (#95). Their own chapter because {@code isSpecial()} hides
     * them from JEI entirely, and the first attempt - worked examples pushed into vanilla's Crafting
     * category - buried them instead: seventeen entries spread over six pages of every crafting recipe
     * in the game, with nothing saying these two are the steps the whole mechanic turns on.
     */
    static final RecipeType<AssemblyRecipe> ASSEMBLY =
        RecipeType.create(Recompile.MOD_ID, "assembly", AssemblyRecipe.class);
    static final RecipeType<com.flatts.recompile.content.recipe.BlueprintCraftingRecipe>
        BLUEPRINT_CRAFTING = RecipeType.create(Recompile.MOD_ID, "blueprint_crafting",
            com.flatts.recompile.content.recipe.BlueprintCraftingRecipe.class);
    static final RecipeType<SalvageRecipe> GROWING =
        RecipeType.create(Recompile.MOD_ID, "growing", SalvageRecipe.class);

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "jei");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        // Each category is sized from the same bundled data its recipes are built from, so a table that
        // grows cannot outgrow its own panel. The alternative - a number written here - is what let the
        // seedling lottery draw its third row through the bottom of the box.
        registration.addRecipeCategories(
            new SalvageCategory(SORTING, Component.translatable("jei.recompile.sorting"),
                gui.createDrawableItemStack(new ItemStack(RCItems.SORTING_TARP.get())), true,
                widest(SortingData.HOUSEHOLD, SortingData.BAG, SortingData.RUBBLE)),
            new SalvageCategory(CUTTING, Component.translatable("jei.recompile.cutting"),
                gui.createDrawableItemStack(new ItemStack(RCItems.SCRAP_KNIFE.get())), false, 1),
            new SalvageCategory(BURNING, Component.translatable("jei.recompile.burning"),
                gui.createDrawableItemStack(new ItemStack(RCItems.BURN_BARREL.get())), false, 1),
            new SalvageCategory(TORCH_CUTTING, Component.translatable("jei.recompile.torch_cutting"),
                gui.createDrawableItemStack(new ItemStack(RCItems.CUTTING_TORCH.get())), true,
                widest(SortingData.STEEL_BEAM)),
            new SalvageCategory(PRYING, Component.translatable("jei.recompile.prying"),
                gui.createDrawableItemStack(new ItemStack(RCItems.PRYBAR.get())), true,
                widest(SortingData.BULKY)),
            new SalvageCategory(TEARDOWN, Component.translatable("jei.recompile.teardown"),
                gui.createDrawableItemStack(new ItemStack(RCItems.RECOMPILE_WORKBENCH.get())), true,
                TeardownData.all().stream().mapToInt(e -> e.outputs().size()).max().orElse(1)),
            new AssemblyCategory(ASSEMBLY, Component.translatable("jei.recompile.assembly"),
                gui.createDrawableItemStack(new ItemStack(RCItems.IDEA_FRAGMENT.get())), 4),
            new BlueprintCraftingCategory(BLUEPRINT_CRAFTING,
                Component.translatable("jei.recompile.blueprint_crafting"),
                gui.createDrawableItemStack(new ItemStack(RCItems.BLUEPRINT.get()))),
            new SalvageCategory(GROWING, Component.translatable("jei.recompile.growing"),
                gui.createDrawableItemStack(new ItemStack(RCItems.HYDROPONICS_BAY.get())), true,
                widest(SortingData.SEEDLING)));
    }

    /** The largest output count across these bundled tables. */
    private static int widest(String... tables) {
        int max = 1;
        for (String table : tables) {
            max = Math.max(max, SortingData.outputs(table).size());
        }
        return max;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<SortingData.Weighted> household = SortingData.outputs(SortingData.HOUSEHOLD);
        List<SortingData.Weighted> bag = SortingData.outputs(SortingData.BAG);
        List<SortingData.Weighted> rubble = SortingData.outputs(SortingData.RUBBLE);
        registration.addRecipes(SORTING, List.of(
            new SalvageRecipe(new ItemStack(RCItems.GARBAGE_BLOCK.get()), household),
            new SalvageRecipe(new ItemStack(RCItems.COMPACTED_BALE.get()), household),
            new SalvageRecipe(new ItemStack(RCItems.TRASH_BAG.get()), bag),
            new SalvageRecipe(new ItemStack(RCItems.STONE_RUBBLE.get()), rubble)));

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
            for (RecipeHolder<SmeltingRecipe> holder : synced.byType(VANILLA_SMELTING)) {
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
                SortingData.outputs(SortingData.BULKY, clientRegistries()))));

        // The Hydroponics Bay, in two halves that are the two halves of the mechanic.
        //
        // First the lottery: the Unknown Seedling against the weighted table it rolls, so a player can
        // see what they are gambling for and at what odds. Then one row per growable, each showing the
        // plant producing itself - which is the whole claim of the machine, that the crop is planted
        // rather than consumed, and it is not a claim any recipe elsewhere makes.
        //
        // The tag is read live rather than listed here, so a pack that adds a growable gets a row for
        // free and this list cannot drift from what the machine will actually accept.
        List<SalvageRecipe> growing = new ArrayList<>();
        growing.add(new SalvageRecipe(new ItemStack(RCItems.UNKNOWN_SEEDLING.get()),
            SortingData.outputs(SortingData.SEEDLING)));
        int yield = com.flatts.recompile.RCConfig.HYDROPONICS_YIELD.get();
        for (var holder : net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getTagOrEmpty(com.flatts.recompile.registry.RCTags.HYDROPONIC)) {
            var plantable = holder.value();
            List<SortingData.Weighted> harvest = new ArrayList<>();
            harvest.add(new SortingData.Weighted(
                new ItemStack(HydroponicsBayBlockEntity.yieldOf(plantable), yield), 1.0f));
            // The byproduct shares the row rather than getting one of its own: what comes off wheat is a
            // fact about growing wheat, and a separate entry would read as a second way to make seeds.
            var by = HydroponicsBayBlockEntity.byproductOf(plantable);
            if (by != null) {
                harvest.add(new SortingData.Weighted(
                    new ItemStack(by.item(), by.count()), by.chance()));
            }
            growing.add(new SalvageRecipe(new ItemStack(plantable), harvest));
        }
        registration.addRecipes(GROWING, growing);

        // Blueprint recipes, handed to JEI as themselves rather than reduced to a blueprint-and-result
        // row. The old category answered "what does this sheet make" and left "how do I make a Clean
        // Mattress" unanswered anywhere in the game - which is the question a player is asking when
        // they click an item in JEI.
        //
        // Read from the SYNCED recipes so the list cannot drift from what the table will actually run.
        // Empty before a world is joined, which never happens in practice: JEI starts on world load.
        List<com.flatts.recompile.content.recipe.BlueprintCraftingRecipe> blueprinted =
            new ArrayList<>();
        RecipeMap syncedBlueprints = RCSyncedRecipes.get();
        if (syncedBlueprints != null) {
            for (RecipeHolder<com.flatts.recompile.content.recipe.BlueprintCraftingRecipe> holder
                    : syncedBlueprints.byType(
                        com.flatts.recompile.registry.RCRecipeTypes.BLUEPRINT_CRAFTING.get())) {
                blueprinted.add(holder.value());
            }
        }
        registration.addRecipes(BLUEPRINT_CRAFTING, blueprinted);

        // THE TWO SPECIAL RECIPES, which JEI cannot see on its own.
        //
        // Fragments-into-a-blueprint and mattress-into-a-bed are CustomRecipes: isSpecial() is true, so
        // the recipe book and JEI both skip them. They are also the two most important steps in the
        // whole loop, so skipping them leaves a player with a pile of fragments and nothing telling them
        // what to do with it.
        //
        // They get their OWN chapter, which is the second attempt. The first put worked examples into
        // vanilla's Crafting category: technically listed, practically buried under six pages of every
        // crafting recipe in the game. It also silently dropped the dye - the examples were built with
        // Ingredient.of(stack.getItem()), which keeps the item and throws the components away, so the
        // black bed showed a plain white mattress making it. Passing real ItemStacks through the
        // category keeps what is on them.
        //
        // A worked example is not a duplicate of the logic; it is the one thing a recipe viewer can
        // show for a recipe whose ingredients are a data component. The numbers come from the same
        // places the real recipe reads them, so a retune moves both.
        List<AssemblyRecipe> examples = new ArrayList<>();
        for (Identifier set : com.flatts.recompile.content.item.BlueprintItem.shipped()) {
            // One slot per fragment rather than one stack of four, because a grid of four is what the
            // player will actually lay out and a "4" in the corner of one slot reads as optional.
            List<ItemStack> fragments = new ArrayList<>();
            for (int i = 0; i < fragmentsFor(set); i++) {
                fragments.add(com.flatts.recompile.content.item.IdeaFragmentItem.of(
                    RCItems.IDEA_FRAGMENT.get(), set, 1));
            }
            examples.add(new AssemblyRecipe(fragments,
                com.flatts.recompile.content.item.BlueprintItem.of(RCItems.BLUEPRINT.get(), set)));
        }
        // The BEDS used to be here as worked examples too. They are sixteen ordinary shaped recipes
        // now - one per coloured Clean Mattress - so JEI draws them itself, in a crafting grid, which
        // is what a player expects when they click a bed. Nothing to duplicate here any more.
        registration.addRecipes(ASSEMBLY, examples);

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

        // The power tier (#72). Both need a panel for the same reason: no recipe expresses what they do.
        // The Solar Panel especially - it shipped inert for the mod's whole life and is a machine part in
        // three blueprints, so "this now generates on its own" is exactly the thing a returning player
        // will not think to check.
        // Raw Roach has no recipe that produces it - it comes out of a garbage block - so the panel is
        // the only place the mechanic is stated at all.
        info(registration, RCItems.RAW_ROACH.get(), "raw_roach");

        info(registration, RCItems.SOLAR_PANEL.get(), "solar_panel");
        info(registration, RCItems.BURNER_GENERATOR.get(), "burner_generator");

        // The bay's two costs are water and power, and neither is visible in its category - a panel is
        // the only place to say that it needs both at once, and that the crop stays put.
        // The blueprint mechanic has three items and no recipe expresses any of it: where a fragment
        // comes from, that the sheet is held rather than spent, or that a cabinet has to be touching
        // the table. All three are panels or they are nowhere.
        info(registration, RCItems.BLUEPRINT.get(), "blueprint");
        info(registration, RCItems.IDEA_FRAGMENT.get(), "idea_fragment");
        info(registration, RCItems.FILING_CABINET.get(), "filing_cabinet");
        info(registration, RCItems.cleanMattress(net.minecraft.world.item.DyeColor.WHITE),
            "clean_mattress");

        info(registration, RCItems.HYDROPONICS_BAY.get(), "hydroponics_bay");
        info(registration, RCItems.UNKNOWN_SEEDLING.get(), "unknown_seedling");
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
        // BLASTING, not SMELTING (#91). The Cupola is a blast machine now, which is what gates iron: no
        // vanilla furnace can run a blasting recipe. Left as SMELTING this would be wrong in both
        // directions at once - advertising a cupola that cooks beef and makes glass, and hiding the iron
        // recipes that are the only reason to build one. Exactly the bug the Burn Barrel had before it
        // got its own category.
        registration.addRecipeCatalyst(new ItemStack(RCItems.CUPOLA_FURNACE.get()), RecipeTypes.BLASTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.HYDROPONICS_BAY.get()), GROWING);
        // The station, not the sheet: what a player needs to know is WHERE these can be made, and the
        // answer is the mod's own table and nowhere else.
        registration.addRecipeCatalyst(new ItemStack(RCItems.SCRAP_CRAFTING_TABLE.get()),
            BLUEPRINT_CRAFTING);
    }

    /**
     * The client's registry access, or null when there is no level.
     *
     * <p>Painting variants are a datapack registry, so the Prying category needs this to show recovered
     * paintings as the works they are rather than as blank canvases. Null-safe on purpose: JEI can build
     * its layouts outside a world, and a missing registry should cost a picture, not throw.
     */
    private static net.minecraft.core.HolderLookup.@org.jspecify.annotations.Nullable Provider
            clientRegistries() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        return level == null ? null : level.registryAccess();
    }

    /**
     * How many fragments this blueprint costs, read off the teardown that teaches it.
     *
     * <p>The same lookup {@code FragmentAssemblyRecipe} does, so the example JEI shows and the recipe
     * the table runs cannot disagree. A hardcoded 4 here would be right until the first pack retuned it.
     */
    private static int fragmentsFor(Identifier set) {
        RecipeMap synced = RCSyncedRecipes.get();
        if (synced != null) {
            for (RecipeHolder<com.flatts.recompile.content.recipe.TeardownRecipe> holder
                    : synced.byType(com.flatts.recompile.registry.RCRecipeTypes.TEARDOWN.get())) {
                for (var teach : holder.value().teaches()) {
                    if (teach.recipe().equals(set)) {
                        return teach.scrapsRequired();
                    }
                }
            }
        }
        return com.flatts.recompile.content.recipe.FragmentAssemblyRecipe.DEFAULT_REQUIRED;
    }
}
