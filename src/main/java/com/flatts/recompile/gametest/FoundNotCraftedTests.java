package com.flatts.recompile.gametest;

import com.flatts.recompile.registry.RCTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * The dump gives you objects; your machines give you materials (design P2.11, issue #161).
 *
 * <p>A manufactured object a person would throw away is <b>found</b>, never crafted. The rule was worth
 * building rather than writing down because the pull streams were already almost entirely finished goods
 * - bell, book, bowl, bundle, chest, lead, painting, shears, carpets - while <b>every one of those
 * recipes was still live</b>. A found bucket competing with a craftable one is what makes finding one
 * feel worthless, and closing that is the whole payoff.
 *
 * <p>This walks every recipe the game has loaded and fails if anything in
 * {@link RCTags#FOUND_ONLY} can be crafted. Runtime rather than a scan of shipped JSON, because most of
 * what it has to catch is <b>vanilla's</b> recipes, which are not in this repo.
 */
final class FoundNotCraftedTests {

    private FoundNotCraftedTests() {
    }

    /**
     * Recipe types whose results this sweep cannot read, and why that is safe.
     *
     * <p>{@code Recipe} has no result accessor in 26.1 - {@code getResultItem} is gone - so the only
     * generic way to learn what a recipe makes is {@link RecipeDisplay}, and {@code Recipe.display()} is
     * a <b>default method returning an empty list</b>. Every vanilla recipe implements it; of this mod's
     * own types only the Separator does. So the obvious sweep reports clean while being blind to most of
     * the mod, which is the same shape as the trap issue #161 already names for {@code placementInfo()}.
     *
     * <p>Rather than let that be silent, an unreadable recipe is an <b>error</b> unless its type is
     * listed here with a reason. The mod's own machine recipes cannot produce a vanilla finished good -
     * they make machines, materials and blueprints - so they are exempt; the day one could, its type
     * comes off this list rather than the assertion being loosened.
     *
     * <p>Vanilla's <b>special</b> crafting recipes are handled separately, by {@code isSpecial()} rather
     * than by type: armour dyeing, map cloning, firework assembly and the rest are all
     * {@code minecraft:crafting} alongside every ordinary shaped recipe, so excluding them by type would
     * blind the sweep to the entire crafting table. They have no fixed result by construction, which is
     * exactly why they return no display.
     */
    private static final Set<String> RESULT_NOT_READABLE = Set.of(
        // Teardown takes an object apart. Its results are materials and Idea Fragments by construction,
        // and it is the one schema packs extend - a pack that made a bucket this way is out of scope.
        "recompile:teardown",
        // Fragments assemble into a Blueprint and nothing else.
        "recompile:fragment_assembly",
        // Blueprint crafting makes this mod's own gated items, none of which is a vanilla finished good.
        "recompile:blueprint_crafting",
        // The Separator returns List.of() deliberately; it splits feed into gems and materials.
        "recompile:separating"
    );

    /**
     * Recipes that hand back the item you put in, so they are not a <b>source</b> of it.
     *
     * <p>Dyeing leather boots takes leather boots and gives you leather boots. Trimming a chestplate
     * takes a chestplate. Neither can produce the armour from nothing, so neither undermines "found,
     * not crafted" - a player still has to find the boots before they can dye them.
     *
     * <p><b>Keyed on the SERIALIZER, not the recipe type, and that distinction is load-bearing.</b>
     * {@code crafting_dye}'s type is plain {@code minecraft:crafting}, shared with every ordinary
     * shaped recipe in the game, so excluding it by type would blind this sweep to the entire
     * crafting table - the same trap the {@code isSpecial()} note above already describes.
     *
     * <p>These used to be caught by {@code isSpecial()} returning no display at all. In 26.1 they
     * declare a real result plus a {@code target} equal to it, so they read as ordinary recipes and
     * arrived here as 105 false positives the moment leather armour was tagged.
     */
    private static final Set<String> RETURNS_ITS_OWN_INPUT = Set.of(
        "minecraft:crafting_dye",
        "minecraft:smithing_trim"
    );

    /**
     * Individual recipes exempted by ID, for when a whole serializer is too broad a brush.
     *
     * <p><b>Ender IO's tank empties a CONTAINER</b>: draining an experience bottle hands back the
     * glass bottle it was made from. A glass bottle is in {@code #recompile:found_only}, and the owner
     * ruled 2026-08-21 (#280) that draining a bottle you already have is not manufacturing one.
     *
     * <p><b>By id rather than by serializer, which review of #281 called for.</b> Exempting
     * {@code enderio:tank} wholesale would also cover 19 {@code tank_fill/*_concrete} recipes turning
     * concrete POWDER into concrete blocks, and {@code tank_fill/nutritious_stick} - genuine
     * manufacture with a different output item. None produces a found-only item today, so nothing was
     * broken, but the exemption would have been wider than the ruling behind it and would silently
     * cover the next one that did.
     *
     * <p><b>The caveat on the ruling, recorded rather than glossed.</b> This is not purely a round
     * trip: an experience bottle can also be BOUGHT - villagers arrived with #227 - so a player with
     * emeralds has a narrow route to glass bottles that does not involve finding one. Accepted as the
     * same shape as the wandering trader's saplings, where the cost of getting a villager at all
     * stands in for the gate it walks around.
     */
    private static final Set<String> EXEMPT_RECIPES = Set.of(
        "enderio:tank_empty/glass_bottle"
    );

    static void register() {
        /*
         * Drive it red by leaving the bucket craftable - which is exactly how this was written. The
         * failure names every route, which matters more than it sounds: the bucket had TWO, vanilla's
         * three-iron recipe and a copper one this mod ships itself, and the issue only knew about the
         * first. A sweep that reports the whole set is how the second one gets found.
         */
        RCGameTests.test("nothing_found_only_can_be_crafted", 40, helper -> {
            ServerLevel level = helper.getLevel();
            ContextMap context = SlotDisplayContext.fromLevel(level);

            List<String> craftable = new ArrayList<>();
            Set<String> unreadable = new TreeSet<>();
            int scanned = 0;

            for (RecipeHolder<?> holder : level.getServer().getRecipeManager().recipeMap().values()) {
                scanned++;
                if (RETURNS_ITS_OWN_INPUT.contains(String.valueOf(
                        BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer())))) {
                    continue;
                }
                List<RecipeDisplay> displays = holder.value().display();
                if (displays.isEmpty()) {
                    // A special recipe computes its result from its input - dyeing armour, cloning a
                    // map - so having no fixed result is what it is, not a gap in this sweep.
                    if (holder.value().isSpecial()) {
                        continue;
                    }
                    RecipeType<?> type = holder.value().getType();
                    String id = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(type));
                    if (!RESULT_NOT_READABLE.contains(id)) {
                        unreadable.add(id + " (" + holder.id().identifier() + ")");
                    }
                    continue;
                }
                if (EXEMPT_RECIPES.contains(holder.id().identifier().toString())) {
                    continue;
                }
                for (RecipeDisplay display : displays) {
                    for (ItemStack result : display.result().resolveForStacks(context)) {
                        if (result.is(RCTags.FOUND_ONLY)) {
                            craftable.add(holder.id().identifier() + " ["
                                + BuiltInRegistries.RECIPE_SERIALIZER.getKey(
                                    holder.value().getSerializer()) + "] makes "
                                + BuiltInRegistries.ITEM.getKey(result.getItem()));
                        }
                    }
                }
            }

            // A sweep over nothing passes forever. The game ships well over a thousand recipes.
            helper.assertTrue(scanned > 500,
                "expected the full recipe set, scanned only " + scanned);

            helper.assertTrue(unreadable.isEmpty(),
                "these recipe types do not implement display(), so this sweep cannot see what they make "
                    + "and is silently blind to them - implement display() or add the type to "
                    + "RESULT_NOT_READABLE with a reason: " + unreadable);

            helper.assertTrue(craftable.isEmpty(),
                "these items are tagged #recompile:found_only but can still be crafted, so finding one "
                    + "is worthless (" + craftable.size() + "): " + craftable);
            helper.succeed();
        });

        /*
         * The other half, and the half a rule like this quietly loses. Disabling a recipe without adding
         * a source does not make an item found - it makes it unobtainable, and the symptom is a player
         * who simply never sees one rather than an error anybody can act on.
         */
        RCGameTests.test("everything_found_only_is_actually_findable", 40, helper -> {
            ServerLevel level = helper.getLevel();
            List<String> unreachable = new ArrayList<>();

            for (var entry : BuiltInRegistries.ITEM.entrySet()) {
                ItemStack stack = new ItemStack(entry.getValue());
                if (!stack.is(RCTags.FOUND_ONLY)) {
                    continue;
                }
                if (!LootSearch.anyTableCanDrop(level, entry.getValue())) {
                    unreachable.add(String.valueOf(entry.getKey().identifier()));
                }
            }

            helper.assertTrue(unreachable.isEmpty(),
                "these items are found-only but no loot table drops them, so they cannot be obtained at "
                    + "all (" + unreachable.size() + "): " + unreachable);
            helper.succeed();
        });
    }
}
