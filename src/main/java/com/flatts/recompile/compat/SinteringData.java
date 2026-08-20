package com.flatts.recompile.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The bundled {@code recompile:sintering} recipes, read for the viewers (#236).
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
public final class SinteringData {

    private static List<CookingRecipeData.Entry> cached;

    private SinteringData() {
    }

    /**
     * The bundled sintering recipes, for JEI.
     *
     * <p><b>Without a category this verb is invisible.</b> JEI draws vanilla-typed recipes for free and
     * a modded {@code RecipeType} is not one however closely it copies the shape - so a player holding
     * a Blaze Briquette would be told nothing fires it, and the only route to a blaze rod outside a
     * fortress would be a thing you had to already know about. Exactly the reason vitrifying needed
     * one.
     */
    public static synchronized List<CookingRecipeData.Entry> all() {
        if (cached == null) {
            cached = CookingRecipeData.read("recompile:sintering",
                com.flatts.recompile.content.recipe.SinteringRecipe.DEFAULT_COOKING_TIME);
        }
        return cached;
    }
}
