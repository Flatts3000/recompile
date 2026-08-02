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
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The bundled {@code recompile:blueprint_crafting} recipes, for viewers.
 *
 * <p>Read from files rather than the recipe manager, per {@link RecipeFiles}: JEI builds its categories
 * on its own schedule and recipes are not client-synced in 26.1, so a snapshot taken at registration
 * time can be empty. When it is, the player clicks a Clean Mattress and JEI shows only how to dye one -
 * the recipe for making it in the first place is simply absent, with nothing saying so.
 */
public final class BlueprintData {

    /**
     * One blueprint recipe as a viewer needs it.
     *
     * <p>{@code ingredients} is in GRID ORDER with empty slots included, so a viewer can lay it out
     * the way the pattern says. A flat list of what it takes would draw the same items in the wrong
     * places, which for a shaped recipe is simply wrong information.
     */
    public record Entry(Identifier blueprint, List<java.util.Optional<Ingredient>> ingredients,
            int width, int height, Item result, int count) {}

    private static List<Entry> cached;

    private BlueprintData() {
    }

    /** Every bundled blueprint recipe, parsed once and cached. */
    public static synchronized List<Entry> all() {
        if (cached != null) {
            return cached;
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonObject recipe : RecipeFiles.ofType("recompile:blueprint_crafting")) {
            Entry entry = read(recipe);
            if (entry != null) {
                entries.add(entry);
            }
        }
        cached = List.copyOf(entries);
        return cached;
    }

    private static Entry read(JsonObject recipe) {
        try {
            Identifier blueprint = Identifier.parse(recipe.get("blueprint").getAsString());
            JsonObject keys = recipe.getAsJsonObject("key");
            List<String> rows = new ArrayList<>();
            for (JsonElement row : recipe.getAsJsonArray("pattern")) {
                rows.add(row.getAsString());
            }
            int width = rows.stream().mapToInt(String::length).max().orElse(0);
            List<java.util.Optional<Ingredient>> ingredients = new ArrayList<>();
            for (String row : rows) {
                for (int col = 0; col < width; col++) {
                    char symbol = col < row.length() ? row.charAt(col) : ' ';
                    if (symbol == ' ') {
                        ingredients.add(java.util.Optional.empty());
                        continue;
                    }
                    if (!keys.has(String.valueOf(symbol))) {
                        return null;   // a pattern symbol with no key is a recipe we cannot draw
                    }
                    Ingredient ingredient = ingredient(keys.get(String.valueOf(symbol)).getAsString());
                    if (ingredient == null) {
                        return null;
                    }
                    ingredients.add(java.util.Optional.of(ingredient));
                }
            }
            JsonObject result = recipe.getAsJsonObject("result");
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(result.get("item").getAsString()));
            int count = result.has("count") ? result.get("count").getAsInt() : 1;
            return new Entry(blueprint, List.copyOf(ingredients), width, rows.size(),
                item, count);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * How many Idea Fragments make this blueprint, from whichever bundled teardown teaches it.
     *
     * <p>Read from files so it is answerable <b>on the client, at any time</b> - the item tooltip needs
     * it, and a tooltip cannot reach the server's recipe manager. It is the same number
     * {@code FragmentAssemblyRecipe} uses, taken from the same {@code scraps_required} field, so the
     * count a player is shown and the count the recipe demands cannot disagree.
     */
    public static int fragmentsFor(Identifier set) {
        for (JsonObject recipe : RecipeFiles.ofType("recompile:teardown")) {
            if (!recipe.has("teaches")) {
                continue;
            }
            for (JsonElement teach : recipe.getAsJsonArray("teaches")) {
                JsonObject entry = teach.getAsJsonObject();
                if (!set.toString().equals(entry.get("recipe").getAsString())) {
                    continue;
                }
                return entry.has("scraps_required") ? entry.get("scraps_required").getAsInt()
                    : DEFAULT_FRAGMENTS;
            }
        }
        return DEFAULT_FRAGMENTS;
    }

    /** When nothing declares a threshold. Mirrors FragmentAssemblyRecipe's own fallback. */
    public static final int DEFAULT_FRAGMENTS = 4;

    /**
     * One ingredient from its JSON form: a bare item id, or a tag with a {@code #} prefix.
     *
     * <p>Tags are resolved to a live {@code HolderSet} so a viewer cycles through everything the
     * ingredient really accepts. The Clean Mattress takes {@code #minecraft:wool}; drawing only white
     * would state a restriction that does not exist.
     */
    private static Ingredient ingredient(String value) {
        if (value.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(value.substring(1)));
            return BuiltInRegistries.ITEM.get(tag)
                .map(Ingredient::of)
                .orElse(null);
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(value));
        return item == null ? null : Ingredient.of(item);
    }
}
