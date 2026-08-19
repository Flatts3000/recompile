package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.SlagFurnaceBlockEntity;
import com.flatts.recompile.content.recipe.BlueprintCraftingRecipe;
import com.flatts.recompile.content.recipe.FragmentAssemblyRecipe;
import com.flatts.recompile.content.recipe.PulverizingRecipe;
import com.flatts.recompile.content.recipe.SeparatingRecipe;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.content.recipe.VitrifyingRecipe;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCRecipeTypes;
import com.flatts.recompile.registry.RCTags;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * GameTests for the Slag Furnace (#236): the fourth verb, and the obsidian gate.
 *
 * <p>Two things are defended here and they are not the same thing. One is that the machine works -
 * slag goes in, obsidian comes out, the glass leaves down a wired network. The other is that
 * <b>nothing else in the game can do it</b>, which is what {@code material_economy.md} means by
 * obsidian being "made only" and what the Nether gate rides on. The second needs sweeps rather than
 * scenarios, because the way a gate dies is that someone adds a perfectly reasonable recipe somewhere
 * else and no scenario test is looking at it - exactly how the iron gate died the first time (#91).
 */
final class SlagFurnaceTests {

    private SlagFurnaceTests() {
    }

    private static SlagFurnaceBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, RCBlocks.SLAG_FURNACE.get());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(pos))
                instanceof SlagFurnaceBlockEntity furnace) {
            return furnace;
        }
        helper.fail("the slag furnace has no BlockEntity");
        return null;
    }

    /**
     * Whether any recipe of {@code type} accepts this item.
     *
     * <p>Generic rather than taking a {@code RecipeType<?>}: {@code RecipeMap.getRecipesFor} binds
     * the recipe type to its input type, and a wildcard cannot satisfy that. Which is also why the
     * furnace-type check below names its four types one at a time instead of looping a list - a
     * heterogeneous list of cooking types erases back to a wildcard and stops compiling.
     */
    private static <T extends Recipe<SingleRecipeInput>> boolean cooks(GameTestHelper helper,
            RecipeType<T> type, Item item) {
        return !helper.getLevel().getServer().getRecipeManager().recipeMap()
            .getRecipesFor(type, new SingleRecipeInput(new ItemStack(item)), helper.getLevel())
            .toList().isEmpty();
    }

    /** Record a leak if this item cooks in a furnace type someone else owns a machine for. */
    private static <T extends Recipe<SingleRecipeInput>> void noteIfCooks(GameTestHelper helper,
            RecipeType<T> type, Item item, List<String> leaks) {
        if (cooks(helper, type, item)) {
            leaks.add(BuiltInRegistries.ITEM.getKey(item) + " also cooks in "
                + BuiltInRegistries.RECIPE_TYPE.getKey(type));
        }
    }

    /** Every item in {@code #recompile:vitrifiable}. */
    private static Set<Item> vitrifiable() {
        Set<Item> tagged = new LinkedHashSet<>();
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(RCTags.VITRIFIABLE)) {
            tagged.add(holder.value());
        }
        return tagged;
    }

    /**
     * Everything a recipe can produce, or {@code null} if this sweep cannot tell.
     *
     * <p>{@code Recipe.display()} is the generic answer and it is <b>empty for this mod's own recipe
     * types</b> - the same blindness {@code FoundNotCraftedTests} documents. A sweep that only read
     * displays would quietly not look at teardown, separating, pulverizing or blueprint crafting,
     * which is exactly where a pack extends the mod. So each of those is read through its own
     * accessor, and anything still unreadable comes back null so the caller can fail rather than
     * skip.
     */
    private static List<Item> outputsOf(Recipe<?> recipe, ContextMap context) {
        List<Item> out = new ArrayList<>();
        switch (recipe) {
            case TeardownRecipe teardown -> teardown.everyPossibleOutput().forEach(out::add);
            case SeparatingRecipe separating -> {
                separating.results().forEach(result -> out.add(result.item()));
                separating.byproducts().forEach(result -> out.add(result.item()));
            }
            case PulverizingRecipe pulverizing -> out.add(pulverizing.result().item());
            case BlueprintCraftingRecipe blueprint -> out.add(blueprint.result().item());
            // Always a Blueprint, whatever the fragments were. Not a gap: it is a fixed output the
            // class computes rather than declares, so there is nothing to read and nothing to miss.
            case FragmentAssemblyRecipe ignored -> out.add(RCItems.BLUEPRINT.get());
            default -> {
                List<RecipeDisplay> displays = recipe.display();
                if (displays.isEmpty()) {
                    // A special recipe computes its result from its input - dyeing armour, cloning a
                    // map. Having no fixed result is what it is, not a gap in this sweep.
                    return recipe.isSpecial() ? out : null;
                }
                for (RecipeDisplay display : displays) {
                    for (ItemStack stack : display.result().resolveForStacks(context)) {
                        out.add(stack.getItem());
                    }
                }
            }
        }
        return out;
    }

    public static void register() {

        // --------------------------------------------------------------- it works

        // The chain's payoff, end to end through the real ticker rather than by calling the recipe:
        // fuel in, slag in, wait. A vitrifying cook is 400 ticks, which is why this is the slowest
        // test here.
        RCGameTests.test("the_slag_furnace_vitrifies_slag_into_obsidian", 600, helper -> {
            SlagFurnaceBlockEntity furnace = place(helper, new BlockPos(1, 1, 1));
            furnace.setItem(0, new ItemStack(RCItems.SLAG.get(), 4));
            furnace.setItem(1, new ItemStack(Items.COAL, 8));
            helper.succeedWhen(() -> {
                helper.assertTrue(furnace.getItem(2).is(Items.OBSIDIAN),
                    "slag in the input and fuel in the fuel slot must eventually make obsidian; the "
                        + "result slot holds " + furnace.getItem(2));
                // The RATIO, pinned. A vitrifying recipe is a cooking recipe and vanilla cooking
                // consumes exactly one item per cook - there is no count on the ingredient - so this
                // is one slag per block whatever any comment says. It shipped with three separate
                // descriptions claiming four, which is the kind of drift a test is cheaper than.
                int made = furnace.getItem(2).getCount();
                int left = furnace.getItem(0).getCount();
                helper.assertTrue(4 - left == made,
                    "one slag per obsidian: " + made + " made but " + (4 - left) + " consumed");
            });
        });

        // Output routing, and it is not cosmetic: this failed silently before it was tested.
        // ScrapNetwork.collect returns an EMPTY member list when the block it is asked to flood from
        // is not itself in #recompile:scrap_connectable - so a machine that calls insertFromMember
        // without being tagged does not route badly, it routes nothing at all, and nothing is logged.
        // Joining the tag is part of shipping a machine that drains, not a decoration on top of one.
        RCGameTests.test("slag_furnace_pushes_its_output_into_connected_storage", 40, helper -> {
            BlockPos barrelPos = new BlockPos(2, 1, 1);
            SlagFurnaceBlockEntity furnace = place(helper, new BlockPos(1, 1, 1));
            helper.setBlock(barrelPos, RCBlocks.SCRAP_BARREL.get());
            var barrel = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(barrelPos));

            furnace.setItem(2, new ItemStack(Items.OBSIDIAN, 3));
            furnace.drainOutput(helper.getLevel());

            helper.assertTrue(furnace.getItem(2).isEmpty(),
                "finished glass must leave the result slot when storage is connected");
            int inBarrel = 0;
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                if (barrel.getItem(slot).is(Items.OBSIDIAN)) {
                    inBarrel += barrel.getItem(slot).getCount();
                }
            }
            helper.assertTrue(inBarrel == 3,
                "and land in the connected barrel; found " + inBarrel + " there");
            helper.succeed();
        });

        // With nothing wired it must NOT vanish. A machine that eats its own output when it has
        // nowhere to send it is worse than one that never routed at all.
        RCGameTests.test("a_lone_slag_furnace_keeps_its_output", 40, helper -> {
            SlagFurnaceBlockEntity furnace = place(helper, new BlockPos(1, 1, 1));
            furnace.setItem(2, new ItemStack(Items.OBSIDIAN, 3));
            furnace.drainOutput(helper.getLevel());
            helper.assertTrue(furnace.getItem(2).getCount() == 3,
                "with no storage connected the output stays put, to be taken through the screen");
            helper.succeed();
        });

        // It costs a whole Cupola to build, so losing one to a misplacement would be brutal.
        // BROKEN BY A SURVIVAL PLAYER, not by Level.destroyBlock.
        //
        // This test was vacuous when written, and vacuous in the exact way it was written to prevent.
        // Level.destroyBlock calls Block.dropResources unconditionally; the correct-tool gate lives in
        // ServerPlayerGameMode.destroyBlock, which that path never touches. So it passed green against
        // a block carrying requiresCorrectToolForDrops while sitting in no mineable tag - meaning no
        // tool in the game was correct, and a player breaking their own furnace got nothing back.
        //
        // makeMockServerPlayerInLevel() sets instabuild, which is exempt from the whole gate, so the
        // survival call is load-bearing rather than tidy.
        RCGameTests.test("slag_furnace_can_be_picked_back_up", 40, helper -> {
            BlockPos pos = new BlockPos(5, 1, 3);
            helper.setBlock(pos, RCBlocks.SLAG_FURNACE.get());
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            player.gameMode.destroyBlock(helper.absolutePos(pos));
            helper.succeedWhen(
                () -> helper.assertItemEntityPresent(RCItems.SLAG_FURNACE.get(), pos, 2.0));
        });

        // --------------------------------------------------------------- it is the gate

        // Obsidian is "made only" per material_economy.md and the Nether gate rides on that. Sweep
        // EVERY loaded recipe of every type and fail on anything but a vitrifying one that makes it.
        //
        // Deliberately NOT scoped to this mod's recipe ids the way the iron sweep is. Iron had four
        // unreachable vanilla smelting recipes that would have made a scoped test permanently red;
        // obsidian has no vanilla recipe at all, so anything producing it is a real leak whoever
        // shipped it - including a pack, which is the case this most needs to catch.
        RCGameTests.test("obsidian_is_made_only_by_vitrifying", 40, helper -> {
            ServerLevel level = helper.getLevel();
            ContextMap context = SlotDisplayContext.fromLevel(level);
            List<String> leaks = new ArrayList<>();
            Set<String> unreadable = new TreeSet<>();
            int scanned = 0;

            for (RecipeHolder<?> holder : level.getServer().getRecipeManager().recipeMap().values()) {
                scanned++;
                Recipe<?> recipe = holder.value();
                if (recipe.getType() == RCRecipeTypes.VITRIFYING.get()) {
                    continue;
                }
                List<Item> outputs = outputsOf(recipe, context);
                if (outputs == null) {
                    unreadable.add(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())
                        + " (" + holder.id().identifier() + ")");
                    continue;
                }
                if (outputs.contains(Items.OBSIDIAN)) {
                    leaks.add(holder.id().identifier() + " ["
                        + BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()) + "]");
                }
            }

            // A sweep over nothing passes forever. The game ships well over a thousand recipes.
            helper.assertTrue(scanned > 500,
                "expected the full recipe set, scanned only " + scanned);
            helper.assertTrue(unreadable.isEmpty(),
                "this sweep cannot see what these recipe types make, so it is silently blind to them "
                    + "- teach outputsOf() to read them rather than loosening the check: " + unreadable);
            helper.assertTrue(leaks.isEmpty(),
                "these make obsidian outside the Slag Furnace, which opens the portal gate early: "
                    + leaks);
            helper.succeed();
        });

        // The tag and the recipes must agree in BOTH directions, and the reason is the client.
        //
        // SlagFurnaceMenu.canSmelt is overridden onto #recompile:vitrifiable rather than onto the
        // recipe map, because vanilla builds its RecipePropertySets from a fixed list of recipe types
        // and a modded one gets none, while the full recipe map is server-side and canSmelt runs on
        // both sides for click prediction. So the TAG is what shift-click and the recipe book actually
        // consult. An input with a recipe but no tag entry cannot be shift-clicked into the machine
        // that melts it; a tag entry with no recipe loads a slot that will never cook. Both are silent.
        RCGameTests.test("the_vitrifiable_tag_matches_the_vitrifying_recipes", 40, helper -> {
            Set<Item> tagged = vitrifiable();
            helper.assertTrue(!tagged.isEmpty(), "#recompile:vitrifiable is empty");

            List<String> unmelted = new ArrayList<>();
            for (Item item : tagged) {
                if (!cooks(helper, RCRecipeTypes.VITRIFYING.get(), item)) {
                    unmelted.add(BuiltInRegistries.ITEM.getKey(item).toString());
                }
            }

            // The other direction, swept over the whole item registry rather than read off the
            // recipes: an Ingredient can match a tag, so "what does this recipe accept" is a wider
            // question than the ids it names, and asking every item is the only answer that cannot
            // be narrower than the truth.
            List<String> untagged = new ArrayList<>();
            int checked = 0;
            for (Item item : BuiltInRegistries.ITEM) {
                checked++;
                if (!tagged.contains(item) && cooks(helper, RCRecipeTypes.VITRIFYING.get(), item)) {
                    untagged.add(BuiltInRegistries.ITEM.getKey(item).toString());
                }
            }
            helper.assertTrue(checked > 500,
                "only " + checked + " items were swept - discovery is broken");
            helper.assertTrue(unmelted.isEmpty(),
                "these are in #recompile:vitrifiable but no recipe melts them, so shift-click loads a "
                    + "slot that will never cook: " + unmelted);
            helper.assertTrue(untagged.isEmpty(),
                "these have a vitrifying recipe but are not in #recompile:vitrifiable, so a player "
                    + "cannot shift-click them into the machine that melts them: " + untagged);
            helper.succeed();
        });

        // The type IS the gate, so its inputs must not be reachable through a type someone else owns a
        // machine for. A vanilla furnace runs smelting, a vanilla blast furnace runs blasting, and the
        // blast furnace IS craftable in this world because iron is reachable through the Cupola - so a
        // stray smelting or blasting recipe on slag would hand the whole chain to a vanilla block.
        //
        // This is the #91 shape stated the other way round. That gate was built from a material the
        // world did not have and died the moment something added the material. This one is built from
        // an operation no other machine performs, so what has to be defended is the operation.
        RCGameTests.test("nothing_vitrifiable_is_reachable_by_a_vanilla_furnace", 20, helper -> {
            Set<Item> tagged = vitrifiable();
            helper.assertTrue(!tagged.isEmpty(), "#recompile:vitrifiable is empty");

            List<String> leaks = new ArrayList<>();
            for (Item item : tagged) {
                noteIfCooks(helper, RecipeType.SMELTING, item, leaks);
                noteIfCooks(helper, RecipeType.BLASTING, item, leaks);
                noteIfCooks(helper, RecipeType.SMOKING, item, leaks);
                noteIfCooks(helper, RecipeType.CAMPFIRE_COOKING, item, leaks);
            }
            helper.assertTrue(leaks.isEmpty(),
                "a vitrifiable input also cooks in a furnace type someone else owns a machine for, so "
                    + "the Slag Furnace is not the only route through it: " + leaks);
            helper.succeed();
        });

        // OBSIDIAN MUST NEVER ENTER A RECIPE BOOK, and this runs vanilla's own filing path rather
        // than reading the flag that causes it.
        //
        // Every RecipeBookCategory that exists belongs to some VANILLA screen's tab list, so filing
        // there offers the recipe in a machine that cannot run it. This shipped reusing
        // BLAST_FURNACE_BLOCKS: a player who pulled obsidian out of a Slag Furnace and then opened a
        // vanilla blast furnace was shown Obsidian in its book, and clicking it loaded their slag into
        // a machine where it would sit forever. Visibly contradicting the "nothing else can vitrify"
        // gate this recipe type exists to enforce.
        //
        // Asserted through RecipeBook.add + contains, not through isSpecial(), so it measures the
        // behaviour rather than restating the implementation - a later change that files the recipe by
        // some other route still fails here.
        RCGameTests.test("obsidian_never_enters_a_recipe_book", 20, helper -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(GameType.SURVIVAL);
            var book = player.getRecipeBook();
            int checked = 0;
            for (RecipeHolder<VitrifyingRecipe> holder : helper.getLevel().recipeAccess().recipeMap()
                    .byType(RCRecipeTypes.VITRIFYING.get())) {
                checked++;
                // addRecipes is what awardUsedRecipesAndPopExperience calls when the player takes
                // the obsidian out, and it returns how many it actually filed. Zero is the whole
                // assertion.
                int added = book.addRecipes(List.of(holder), player);
                helper.assertTrue(added == 0,
                    holder.id() + " was filed into a recipe book, so a vanilla furnace screen whose "
                        + "tabs cover its category will offer it - loading the player's slag into a "
                        + "machine that cannot melt it");
            }
            helper.assertTrue(checked > 0,
                "no vitrifying recipes loaded, so this would pass vacuously");
            helper.succeed();
        });

        // The machine has to be reachable at all, and it is the one thing the two sweeps above cannot
        // notice: a gate nobody can pass is not a gate, it is a dead end. Same failure the
        // found-not-crafted pair guards against - disabling a route without adding one does not make an
        // item found, it makes it unobtainable, and the symptom is a player who simply never sees one.
        RCGameTests.test("the_slag_furnace_itself_is_craftable", 40, helper -> {
            ServerLevel level = helper.getLevel();
            ContextMap context = SlotDisplayContext.fromLevel(level);
            List<String> makes = new ArrayList<>();
            for (RecipeHolder<?> holder : level.getServer().getRecipeManager().recipeMap().values()) {
                List<Item> outputs = outputsOf(holder.value(), context);
                if (outputs != null && outputs.contains(RCItems.SLAG_FURNACE.get())) {
                    makes.add(holder.id().identifier().toString());
                }
            }
            helper.assertTrue(!makes.isEmpty(),
                "nothing makes a Slag Furnace, so the obsidian chain is a dead end");
            helper.succeed();
        });

        // The viewers must describe the machine the game actually runs, and this one has more riding
        // on it than the others: vitrifying is the ONLY route to obsidian, so a category that reads
        // nothing hides the entire chain behind knowing it exists. VitrifyingData parses the bundled
        // JSON rather than the recipe manager (recipes are not client-synced in 26.1), which means it
        // can quietly disagree with the game - and it would fail SILENTLY, since an empty category
        // registers without error. Same failure TeardownData shipped once: it named its recipes in a
        // constant, a third recipe shipped, and every viewer denied it existed.
        RCGameTests.test("jei_sees_every_vitrifying_recipe", 20, helper -> {
            var rows = com.flatts.recompile.compat.VitrifyingData.all();
            int inGame = 0;
            for (RecipeHolder<VitrifyingRecipe> holder : helper.getLevel().recipeAccess().recipeMap()
                    .byType(RCRecipeTypes.VITRIFYING.get())) {
                inGame++;
                boolean matched = false;
                for (var row : rows) {
                    if (holder.value().matches(new SingleRecipeInput(row.input()),
                            helper.getLevel())) {
                        matched = true;
                    }
                }
                helper.assertTrue(matched, "JEI does not show " + holder.id());
            }
            helper.assertTrue(inGame > 0 && rows.size() == inGame,
                "the game runs " + inGame + " vitrifying recipes and JEI reads " + rows.size());

            // And the OUTPUT, because reading the input right proves only half of it. The result is
            // an ItemStackTemplate and spells its field `id` where an ingredient is a bare string or
            // an `item` object - two shapes in one file, and a parser that handles only the second
            // yields a row with an empty output that draws as a blank box.
            for (var row : rows) {
                helper.assertTrue(!row.outputs().isEmpty() && !row.outputs().get(0).stack().isEmpty(),
                    "a vitrifying row parsed its input but not its result, so JEI would draw an "
                        + "empty output box for " + row.input());
            }
            helper.succeed();
        });

        // Registry hygiene the sweeps assume. VITRIFYING has to be a PUBLIC recipe type - a pack
        // extends the chain by shipping JSON, the way separating and pulverizing already are - and an
        // unregistered type would make every sweep above pass vacuously rather than fail.
        RCGameTests.test("vitrifying_is_a_registered_public_recipe_type", 20, helper -> {
            Identifier id = BuiltInRegistries.RECIPE_TYPE.getKey(RCRecipeTypes.VITRIFYING.get());
            helper.assertTrue(id != null, "recompile:vitrifying is not in the recipe type registry");
            helper.assertTrue(Recompile.MOD_ID.equals(id.getNamespace()),
                "the vitrifying recipe type is registered under " + id.getNamespace());
            helper.assertTrue(cooks(helper, RCRecipeTypes.VITRIFYING.get(), RCItems.SLAG.get()),
                "the type is registered but slag does not melt, so every gate sweep here passes "
                    + "vacuously");
            helper.succeed();
        });
    }
}
