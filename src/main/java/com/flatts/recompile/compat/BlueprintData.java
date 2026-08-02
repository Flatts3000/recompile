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

    /** One blueprint recipe as a viewer needs it. */
    public record Entry(Identifier blueprint, List<Ingredient> ingredients, Item result, int count) {}

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
            List<Ingredient> ingredients = new ArrayList<>();
            for (JsonElement element : recipe.getAsJsonArray("ingredients")) {
                Ingredient ingredient = ingredient(element.getAsString());
                if (ingredient == null) {
                    return null;   // an ingredient we cannot render is a recipe we should not draw
                }
                ingredients.add(ingredient);
            }
            JsonObject result = recipe.getAsJsonObject("result");
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(result.get("item").getAsString()));
            int count = result.has("count") ? result.get("count").getAsInt() : 1;
            return new Entry(blueprint, List.copyOf(ingredients), item, count);
        } catch (RuntimeException e) {
            return null;
        }
    }

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
