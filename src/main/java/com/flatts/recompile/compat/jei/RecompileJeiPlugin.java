package com.flatts.recompile.compat.jei;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.compat.MultiblockParts;
import com.flatts.recompile.compat.SearchAliases;
import com.flatts.recompile.compat.JeiInfoPanels;
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
import mezz.jei.api.registration.IIngredientAliasRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * JEI integration: surfaces the mechanics that are not vanilla recipes and so are
 * otherwise undiscoverable. Loaded only when JEI is present (the API is {@code compileOnly}).
 *
 * <ul>
 *   <li><b>Sorting</b> - what every pickable-through block gives up, derived from the block
 *       registry rather than listed, so a new pile variant appears here the day it exists.</li>
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
    /**
     * The gem tier's grinder (docs/gem_tier_spec.md). Deterministic, so the odds column is off - a
     * separator splits a feed rather than rolling on it, and a "100%" beside every row would be noise.
     */
    static final RecipeType<SalvageRecipe> SEPARATING =
        RecipeType.create(Recompile.MOD_ID, "separating", SalvageRecipe.class);

    /**
     * The mill (#189). Deterministic, so the odds column is off for the same reason separating's is: a
     * mill does not roll, everything that goes in comes out as the same powder, and a "100%" beside
     * every row would be noise.
     */
    static final RecipeType<SalvageRecipe> PULVERIZING =
        RecipeType.create(Recompile.MOD_ID, "pulverizing", SalvageRecipe.class);

    /**
     * The Cupola (#243). Its recipes are ordinary {@code minecraft:blasting} and appear under vanilla
     * Blasting too - the machine really does run them, and that IS the iron gate. This category exists
     * for the half vanilla's display has no slot for: every Nth completed smelt also rakes off a lump
     * of slag, which is the sole input to the Separator, the Pulverizer and the Slag Furnace. A player
     * reading Blasting was told the Cupola makes a gold nugget and nothing else - true, and materially
     * incomplete, since the whole obsidian chain hangs off the byproduct.
     */
    static final RecipeType<com.flatts.recompile.compat.CupolaData.Entry> CUPOLA =
        RecipeType.create(Recompile.MOD_ID, "cupola",
            com.flatts.recompile.compat.CupolaData.Entry.class);

    /**
     * The vitrifier (#236). Deterministic like the other two, and the only route to obsidian in the
     * game - which is precisely why it cannot be left out. JEI shows vanilla-typed recipes for free
     * and a modded RecipeType is not one however closely it copies the shape, so without a category
     * the machine is invisible: a player holding slag is told nothing melts it.
     */
    static final RecipeType<SalvageRecipe> VITRIFYING =
        RecipeType.create(Recompile.MOD_ID, "vitrifying", SalvageRecipe.class);

    /**
     * Hydrating a Dry Clay Body in a water cauldron - the last step of the clay chain (#115).
     *
     * <p><b>This exists because the step is not a recipe.</b> It is a {@code CauldronInteraction}
     * registered in Java, so JEI cannot find it from either side: a Dry Clay Body showed no uses and a
     * clay ball showed no recipe, and the chain looked like it stopped one step short of the thing it
     * exists to make. That is the "a mechanic nobody can see does not exist" case the categories at the
     * top of this file are for - the same reason Prying and Sorting have one.
     */
    static final RecipeType<SalvageRecipe> HYDRATING =
        RecipeType.create(Recompile.MOD_ID, "hydrating", SalvageRecipe.class);

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
            new SalvageCategory(SEPARATING, Component.translatable("jei.recompile.separating"),
                gui.createDrawableItemStack(new ItemStack(RCItems.SEPARATOR.get())), false,
                com.flatts.recompile.compat.SeparatingData.all().stream()
                    .mapToInt(e -> e.outputs().size()).max().orElse(1)),
            new SalvageCategory(HYDRATING, Component.translatable("jei.recompile.hydrating"),
                gui.createDrawableItemStack(new ItemStack(Items.WATER_BUCKET)), false, 1),
            new SalvageCategory(PULVERIZING, Component.translatable("jei.recompile.pulverizing"),
                gui.createDrawableItemStack(new ItemStack(RCItems.PULVERIZER.get())), false,
                com.flatts.recompile.compat.PulverizingData.all().stream()
                    .mapToInt(e -> e.outputs().size()).max().orElse(1)),
            new CupolaCategory(CUPOLA, Component.translatable("jei.recompile.cupola"),
                gui.createDrawableItemStack(new ItemStack(RCItems.CUPOLA_FURNACE.get()))),
            new SalvageCategory(VITRIFYING, Component.translatable("jei.recompile.vitrifying"),
                gui.createDrawableItemStack(new ItemStack(RCItems.SLAG_FURNACE.get())), false,
                com.flatts.recompile.compat.VitrifyingData.all().stream()
                    .mapToInt(e -> e.outputs().size()).max().orElse(1)),
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
            max = Math.max(max, SortingData.visibleOutputs(table).size());
        }
        return max;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // Mechanical Waste was missing here for its whole life, and the symptom was subtle: clicking
        // Magnet Scrap in JEI showed NOTHING AT ALL. Its only source is this stream, block drops are
        // invisible to JEI, and no recipe makes it - so the panel was simply empty, which reads as a
        // broken item rather than a missing entry. Quartz Grit, Spent Abrasive and the Motor were all
        // in the same state. `every_sortable_block_is_a_jei_sorting_source` now asserts the list is
        // complete, derived from the registry rather than maintained here.
        List<SalvageRecipe> sorting = new ArrayList<>();
        for (SortingData.SortingSource source : SortingData.sortingSources()) {
            sorting.add(new SalvageRecipe(new ItemStack(source.block()),
                SortingData.visibleOutputs(source.path())));
        }
        registration.addRecipes(SORTING, sorting);

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
                SortingData.visibleOutputs(SortingData.STEEL_BEAM))));

        // One row, and it is the whole mechanic: the blend goes in, clay comes out, and the cauldron
        // is the catalyst rather than an ingredient because it is not consumed - only a level of its
        // water is.
        registration.addRecipes(HYDRATING, List.of(
            new SalvageRecipe(new ItemStack(RCItems.DRY_CLAY_BODY.get()),
                List.of(new com.flatts.recompile.compat.SortingData.Weighted(
                    new ItemStack(Items.CLAY_BALL), 1.0F)))));

        registration.addRecipes(PRYING, List.of(
            new SalvageRecipe(new ItemStack(RCItems.BULKY_WASTE.get()),
                SortingData.visibleOutputs(SortingData.BULKY, clientRegistries()))));

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
            SortingData.visibleOutputs(SortingData.SEEDLING)));
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

        // Blueprint recipes, drawn as recipes rather than reduced to a blueprint-and-result row. The
        // old category answered "what does this sheet make" and left "how do I make a Clean Mattress"
        // unanswered anywhere in the game, which is the question a player is asking when they click an
        // item in JEI.
        //
        // Read from the bundled FILES, not the recipe manager. Recipes are not client-synced in 26.1
        // and JEI builds its categories on its own schedule, so a snapshot taken here can be empty -
        // and an empty category is not an error, it is a recipe the player cannot find with nothing
        // saying why. See RecipeFiles.
        List<com.flatts.recompile.content.recipe.BlueprintCraftingRecipe> blueprinted =
            new ArrayList<>();
        for (com.flatts.recompile.compat.BlueprintData.Entry e
                : com.flatts.recompile.compat.BlueprintData.all()) {
            blueprinted.add(new com.flatts.recompile.content.recipe.BlueprintCraftingRecipe(
                e.blueprint(),
                new net.minecraft.world.item.crafting.ShapedRecipePattern(
                    e.width(), e.height(), e.ingredients(), java.util.Optional.empty()),
                new com.flatts.recompile.content.recipe.BlueprintCraftingRecipe.Result(
                    e.result(), e.count())));
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
            for (int i = 0; i < com.flatts.recompile.compat.BlueprintData.fragmentsFor(set); i++) {
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

        // Separating, from the bundled recipe JSON for the same reason teardown is: the manager is
        // empty when JEI asks. The input stack carries its real count, because "sixteen scrap for one
        // diamond" IS the tier and a row showing one item would describe a different machine.
        List<SalvageRecipe> separating = new ArrayList<>();
        for (com.flatts.recompile.compat.SeparatingData.Entry entry
                : com.flatts.recompile.compat.SeparatingData.all()) {
            separating.add(new SalvageRecipe(entry.input(), entry.outputs()));
        }
        if (!separating.isEmpty()) {
            registration.addRecipes(SEPARATING, separating);
        }

        // Pulverizing, read the same way and for the same reason. Without this the whole recipe type is
        // invisible: a player holding a bone has nothing telling them a mill turns it into four meal,
        // and a new type nobody can see is a mechanic that does not exist as far as the game is
        // concerned.
        List<SalvageRecipe> pulverizing = new ArrayList<>();
        for (com.flatts.recompile.compat.PulverizingData.Entry entry
                : com.flatts.recompile.compat.PulverizingData.all()) {
            pulverizing.add(new SalvageRecipe(entry.inputs(), entry.outputs()));
        }
        if (!pulverizing.isEmpty()) {
            registration.addRecipes(PULVERIZING, pulverizing);
        }

        // Vitrifying, same shape again. This one matters most of the three: it is the ONLY route to
        // obsidian, so a player who cannot see it here has no way to learn the chain exists short of
        // reading the guidebook cover to cover.
        List<SalvageRecipe> vitrifying = new ArrayList<>();
        for (com.flatts.recompile.compat.VitrifyingData.Entry entry
                : com.flatts.recompile.compat.VitrifyingData.all()) {
            vitrifying.add(new SalvageRecipe(entry.inputs(), entry.outputs()));
        }
        if (!vitrifying.isEmpty()) {
            registration.addRecipes(VITRIFYING, vitrifying);
        }

        // The Cupola's own rows, carrying the slag vanilla Blasting cannot draw.
        var cupola = com.flatts.recompile.compat.CupolaData.all();
        if (!cupola.isEmpty()) {
            registration.addRecipes(CUPOLA, cupola);
        }

        // Machines only, not their parts. A crafted core says nothing about the tower it needs, and
        // JEI is where a player goes looking. The parts already have recipes here, and the appliance
        // already has a teardown entry, so a panel on those would only restate what JEI shows.
        // The Burn Barrel is a catalyst for SMELTING, but since it went refuse-only that is only true of
        // SOME smelting recipes - JEI has no way to attach a catalyst to a subset, so it would otherwise
        // tell a player they can smelt iron or glass in a barrel that refuses both. The panel is where
        // that gets said. Same for the two mechanics no recipe expresses: the torch's charge, and the
        // Cupola being the machine that lifts the barrel's restriction.
        // Iterated from com.flatts.recompile.compat.JeiInfoPanels rather than written out here.
        // This list lived in this file for the mod's whole life, and because this class only loads
        // when JEI is present, nothing in the build could check it - so a lang string written without
        // a matching call was a translation nobody could ever see. It happened three times before it
        // was caught, and the guard then found two more that had been dead for months.
        for (JeiInfoPanels.Panel panel : JeiInfoPanels.all()) {
            info(registration, panel.item(), panel.key());
        }
    }

    private static void info(IRecipeRegistration registration, net.minecraft.world.level.ItemLike item,
            String key) {
        registration.addIngredientInfo(item, Component.translatable("jei.recompile.info." + key));
    }

    /**
     * The "+" button that fills the grid from your inventory.
     *
     * <p><b>Registered per category, and a category with no handler simply has no button.</b> Nothing
     * warns you: the recipe renders, the transfer arrow is absent, and it reads as JEI deciding the
     * recipe is not craftable. Blueprint recipes and fragment assembly are both crafted in this table's
     * 3x3, so both get one.
     *
     * <p>Slot arithmetic comes from {@code ScrapCraftingStationMenu}'s own order: 0 is the result, 1
     * through 9 are the grid, and the player's 36 follow. The result slot is deliberately outside the
     * range - handing JEI a range that includes it would let a transfer overwrite what the table just
     * made.
     */
    /**
     * The "+" button that fills the grid.
     *
     * <p><b>A custom handler, not JEI's built-in one.</b> The built-in moves items between the open
     * container's own slots, and this table's materials mostly are not in it - they are in the Scrap
     * Barrel and Bins wired to it. With the stock handler a player whose every ingredient sat in the
     * barrel beside them was told "Missing Items", which is the exact thing the connected-storage panel
     * exists to stop being true.
     *
     * <p>Registered per category, and a category without a handler simply has no button - nothing warns
     * you, the transfer arrow is just absent and it reads as JEI deciding the recipe is uncraftable. So
     * every category this table can run gets one, vanilla crafting included.
     */
    @Override
    public void registerRecipeTransferHandlers(
            mezz.jei.api.registration.IRecipeTransferRegistration registration) {
        var helper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(
            new ScrapTableTransfer<>(BLUEPRINT_CRAFTING, helper), BLUEPRINT_CRAFTING);
        registration.addRecipeTransferHandler(
            new ScrapTableTransfer<>(ASSEMBLY, helper), ASSEMBLY);
        registration.addRecipeTransferHandler(
            new ScrapTableTransfer<>(RecipeTypes.CRAFTING, helper), RecipeTypes.CRAFTING);
    }

    /**
     * The renamed vanilla items answer to their old names in search (#118).
     *
     * <p>A player looking for a lead types "lead". Renaming it to Rope without this makes the item
     * unfindable by the only word they have for it, and every wiki page and video still uses that word.
     * The alias is a <b>translation key</b>, so it translates with the pack rather than pinning search
     * to English.
     */
    @Override
    public void registerIngredientAliases(IIngredientAliasRegistration registration) {
        SearchAliases.all().forEach((item, alias) ->
            registration.addAlias(new ItemStack(item), alias));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // The world's only crafting station - so JEI stops telling players to use a
        // vanilla table they can never make.
        registration.addCraftingStation(RecipeTypes.CRAFTING, RCItems.SCRAP_CRAFTING_TABLE.get());

        registration.addRecipeCatalyst(new ItemStack(RCItems.SORTING_TARP.get()), SORTING);
        // THE TROMMEL, not the Separator (#187). The Separator sorted at exactly the tarp's rate and
        // was listed here for it; it has one verb now and sorting moved to the machine that can make
        // a discrimination. Listing a machine under a category it no longer serves teaches a player
        // to build the wrong thing.
        registration.addRecipeCatalyst(new ItemStack(RCItems.TROMMEL.get()), SORTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.GARBAGE_BLOCK.get()), SORTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.TRASH_BAG.get()), SORTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.COMPACTED_BALE.get()), SORTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.SCRAP_KNIFE.get()), CUTTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.CUTTING_TORCH.get()), TORCH_CUTTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.PRYBAR.get()), PRYING);
        // THE CAULDRON ONLY. A water bucket was listed here too, on the reasoning that a player looks
        // up whatever they are holding - but a catalyst says "this is the tool for this job", and a
        // bucket cannot hydrate anything. It fills the cauldron, which is a different step. Advertising
        // it would send someone to right-click the blend with a bucket and watch nothing happen, which
        // is the mistake the TORCH_CUTTING note above already records: a catalyst attaches to a
        // CATEGORY, and must not advertise a job its item cannot do.
        //
        // One entry, because there is one cauldron ITEM - filled is a block state, not a
        // separate item, so the title carries "must already hold water" instead.
        registration.addRecipeCatalyst(new ItemStack(Items.CAULDRON), HYDRATING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.RECOMPILE_WORKBENCH.get()), TEARDOWN);
        registration.addRecipeCatalyst(new ItemStack(RCItems.SEPARATOR.get()), SEPARATING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.PULVERIZER.get()), PULVERIZING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.SLAG_FURNACE.get()), VITRIFYING);
        // The Burn Barrel is NOT a general smelting station - it burns refuse only, so it is the catalyst
        // for its own category, which lists exactly what it takes.
        registration.addRecipeCatalyst(new ItemStack(RCItems.BURN_BARREL.get()), BURNING);
        // BLASTING, not SMELTING (#91). The Cupola is a blast machine now, which is what gates iron: no
        // vanilla furnace can run a blasting recipe. Left as SMELTING this would be wrong in both
        // directions at once - advertising a cupola that cooks beef and makes glass, and hiding the iron
        // recipes that are the only reason to build one. Exactly the bug the Burn Barrel had before it
        // got its own category.
        registration.addRecipeCatalyst(new ItemStack(RCItems.CUPOLA_FURNACE.get()), RecipeTypes.BLASTING);
        registration.addRecipeCatalyst(new ItemStack(RCItems.CUPOLA_FURNACE.get()), CUPOLA);
        registration.addRecipeCatalyst(new ItemStack(RCItems.HYDROPONICS_BAY.get()), GROWING);
        // The station, not the sheet: what a player needs to know is WHERE these can be made, and the
        // answer is the mod's own table and nowhere else.
        registration.addRecipeCatalyst(new ItemStack(RCItems.SCRAP_CRAFTING_TABLE.get()),
            BLUEPRINT_CRAFTING);
    }

    /**
     * Take the uncraftable multiblock parts back out of JEI's item list.
     *
     * <p><b>The rule: a viewer must not list a part the player can never hold</b> (owner, 2026-08-03).
     * A formed cell - a Separator Chamber, a Compost Cage - only exists once a machine is assembled, so
     * showing it teaches nothing except that the mod has a block with no recipe. {@link MultiblockParts}
     * derives the set from the blueprints rather than naming blocks, so this covers a machine written
     * next year without anyone remembering this file exists.
     *
     * <p>Done at runtime rather than by hiding the items themselves: they stay in the creative tab,
     * where a builder legitimately wants them.
     */
    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime runtime) {
        List<ItemStack> hidden = MultiblockParts.hiddenStacks();
        if (!hidden.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, hidden);
        }
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

}
