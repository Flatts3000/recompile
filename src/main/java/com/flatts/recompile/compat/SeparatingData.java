package com.flatts.recompile.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The bundled {@code recompile:separating} recipes, read for the viewers
 * ({@code docs/gem_tier_spec.md}).
 *
 * <p>Reads files rather than the recipe manager for the reason {@link RecipeFiles} states: recipes are
 * not client-synced in 26.1 and the manager is reliably empty when JEI asks. Discovered by walking the
 * recipe folder rather than from a list of paths, which is the mistake {@code TeardownData} already
 * made once - it named two recipes, a third shipped, and the viewers denied it existed.
 *
 * <p><b>The input carries its count.</b> That is the whole point of the tier: sixteen scrap for one
 * diamond. A category that drew a single item would be describing a different machine.
 */
public final class SeparatingData {

    /** One row: the feed with its real count, what comes out, and how long it takes. */
    public record Entry(ItemStack input, List<SortingData.Weighted> outputs, int ticks, int energy) {
    }

    private static List<Entry> cached;

    private SeparatingData() {
    }

    public static synchronized List<Entry> all() {
        if (cached != null) {
            return cached;
        }
        List<Entry> out = new ArrayList<>();
        for (JsonObject recipe : RecipeFiles.ofType("recompile:separating")) {
            ItemStack input = stack(recipe.get("input"), recipe.has("count")
                ? recipe.get("count").getAsInt() : 1);
            if (input.isEmpty()) {
                continue;
            }
            List<SortingData.Weighted> outputs = new ArrayList<>();
            // Results first, then byproducts, so the thing the player came for reads left to right.
            // Both are certain - a separator splits a feed, it does not roll - so chance is 1.
            collect(recipe, "results", outputs);
            collect(recipe, "byproducts", outputs);
            if (outputs.isEmpty()) {
                continue;
            }
            out.add(new Entry(input, List.copyOf(outputs),
                recipe.has("ticks") ? recipe.get("ticks").getAsInt() : 200,
                recipe.has("energy") ? recipe.get("energy").getAsInt() : 16));
        }
        cached = List.copyOf(out);
        return cached;
    }

    private static void collect(JsonObject recipe, String key, List<SortingData.Weighted> into) {
        if (!recipe.has(key) || !recipe.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement element : recipe.getAsJsonArray(key)) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            ItemStack stack = stack(entry.get("item"),
                entry.has("count") ? entry.get("count").getAsInt() : 1);
            if (!stack.isEmpty()) {
                into.add(new SortingData.Weighted(stack, 1.0F));
            }
        }
    }

    /** An item id (possibly wrapped in an ingredient object) as a stack of the given size. */
    private static ItemStack stack(JsonElement element, int count) {
        if (element == null) {
            return ItemStack.EMPTY;
        }
        String id = null;
        if (element.isJsonPrimitive()) {
            id = element.getAsString();
        } else if (element.isJsonObject() && element.getAsJsonObject().has("item")) {
            id = element.getAsJsonObject().get("item").getAsString();
        }
        if (id == null || id.startsWith("#")) {
            return ItemStack.EMPTY;   // a tag input has no single icon; none ship today
        }
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(parsed);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }
}
