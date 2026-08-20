package com.flatts.recompile.compat;

import java.util.List;

/**
 * The bundled {@code recompile:vitrifying} recipes, read for the viewers (#236).
 *
 * <p>Reads files rather than the recipe manager for the reason {@link RecipeFiles} states: recipes are
 * not client-synced in 26.1 and the manager is reliably empty when JEI asks.
 *
 * <p><b>The input carries no count, and that is the type's shape rather than an omission here.</b>
 * Vitrifying borrows vanilla's cooking schema wholesale, and a vanilla cooking ingredient has no count
 * - one item goes in per cook. Separating and pulverizing both draw a feed count because {@code count}
 * is their ratio dial; drawing one here would be inventing a number the recipe cannot express, and
 * three separate descriptions of this machine already claimed four slag per block before anyone ran it.
 *
 * <p><b>Why the type needs a category at all.</b> JEI shows vanilla-typed recipes for free, and a
 * modded {@code RecipeType} is not one however closely it copies the shape - so without this the whole
 * machine is invisible: a player holding a lump of slag would be told nothing melts it, and the only
 * route to obsidian in the game would be a thing you had to already know about.
 */
public final class VitrifyingData {

    private static List<CookingRecipeData.Entry> cached;

    private VitrifyingData() {
    }

    /**
     * The bundled vitrifying recipes.
     *
     * <p>The parsing moved to {@link CookingRecipeData} when sintering became the second cooking-shaped
     * type (#248) - copying it would have duplicated a hundred and twenty lines whose corrections
     * (the JSON array branch, {@code id} versus {@code item}, AIR-not-null) were each paid for once.
     */
    public static synchronized List<CookingRecipeData.Entry> all() {
        if (cached == null) {
            cached = CookingRecipeData.read("recompile:vitrifying",
                com.flatts.recompile.content.recipe.VitrifyingRecipe.DEFAULT_COOKING_TIME);
        }
        return cached;
    }
}
