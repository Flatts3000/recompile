package com.flatts.recompile.compat;

import java.util.List;

/**
 * The bundled {@code recompile:sintering} recipes, read for the viewers (#248).
 *
 * <p>Reads FILES rather than the recipe manager, for the reason {@link RecipeFiles} states: recipes are
 * not client-synced in 26.1 and the manager is reliably empty when JEI asks. The parsing itself lives in
 * {@link CookingRecipeData}, shared with vitrifying - copying it would have duplicated a hundred and
 * twenty lines whose corrections were each paid for once.
 *
 * <p><b>Why the type needs a category at all.</b> JEI shows vanilla-typed recipes for free, and a modded
 * {@code RecipeType} is not one however closely it copies vanilla's cooking shape. Without a category
 * this whole verb is invisible: a player holding a Blaze Briquette would be told nothing fires it, and
 * the only route to a blaze rod outside a fortress would be a thing you had to already know about.
 *
 * <p><b>The input carries no count, and that is the schema rather than an omission here.</b> Sintering
 * borrows vanilla's cooking shape wholesale and a cooking ingredient has no count - one item per firing.
 * The four-powder price of a rod lives in the Blaze Briquette's own crafting recipe, which is where it
 * has to live: a one-for-one cooking recipe against vanilla's rod-to-two-powder crafting would be an
 * infinite loop rather than a chain.
 */
public final class SinteringData {

    private static List<CookingRecipeData.Entry> cached;

    private SinteringData() {
    }

    /** Every bundled sintering recipe. */
    public static synchronized List<CookingRecipeData.Entry> all() {
        if (cached == null) {
            cached = CookingRecipeData.read("recompile:sintering",
                com.flatts.recompile.content.recipe.SinteringRecipe.DEFAULT_COOKING_TIME);
        }
        return cached;
    }
}
