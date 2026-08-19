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

    /**
     * One row: what the furnace accepts, what it becomes, and how long it takes.
     *
     * <p>{@code inputs} is a LIST because an ingredient may be a tag, and JEI cycles a multi-item
     * input natively - so a tag-input recipe shows what it really accepts rather than one arbitrary
     * member. Dropping tags is the bug {@code PulverizingData} records having shipped once.
     */
    public record Entry(List<ItemStack> inputs, List<SortingData.Weighted> outputs, int ticks) {

        /** The first accepted item, for callers that can only draw one. */
        public ItemStack input() {
            return inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0);
        }
    }

    private static List<Entry> cached;

    private VitrifyingData() {
    }

    public static synchronized List<Entry> all() {
        if (cached != null) {
            return cached;
        }
        List<Entry> out = new ArrayList<>();
        for (JsonObject recipe : RecipeFiles.ofType("recompile:vitrifying")) {
            List<ItemStack> inputs = stacks(recipe.get("ingredient"));
            if (inputs.isEmpty()) {
                continue;
            }
            ItemStack result = ItemStack.EMPTY;
            if (recipe.has("result") && recipe.get("result").isJsonObject()) {
                JsonObject entry = recipe.getAsJsonObject("result");
                result = stack(entry.get("id"),
                    entry.has("count") ? entry.get("count").getAsInt() : 1);
            }
            if (result.isEmpty()) {
                continue;
            }
            // Chance 1: melting is not a roll. The same silicates come out every time, which is why
            // an odds column beside every row would be noise - the same call separating and
            // pulverizing already made.
            out.add(new Entry(inputs, List.of(new SortingData.Weighted(result, 1.0F)),
                recipe.has("cookingtime") ? recipe.get("cookingtime").getAsInt() : 300));
        }
        cached = List.copyOf(out);
        return cached;
    }

    /** Every item an ingredient accepts. A tag expands to its members rather than being dropped. */
    private static List<ItemStack> stacks(JsonElement element) {
        if (element != null && element.isJsonPrimitive()
                && element.getAsString().startsWith("#")) {
            Identifier parsed = Identifier.tryParse(element.getAsString().substring(1));
            if (parsed == null) {
                return List.of();
            }
            TagKey<Item> key = TagKey.create(Registries.ITEM, parsed);
            List<ItemStack> out = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (new ItemStack(item).is(key)) {
                    out.add(new ItemStack(item));
                }
            }
            return List.copyOf(out);
        }
        ItemStack single = stack(element, 1);
        return single.isEmpty() ? List.of() : List.of(single);
    }

    /**
     * An item id as a stack.
     *
     * <p>Reads {@code id} as well as {@code item}: vanilla's cooking result is an
     * {@code ItemStackTemplate}, which spells the field {@code id}, while an ingredient is written as
     * a bare string or an {@code item} object. Both shapes appear in one recipe file.
     */
    private static ItemStack stack(JsonElement element, int count) {
        if (element == null) {
            return ItemStack.EMPTY;
        }
        String id = null;
        if (element.isJsonPrimitive()) {
            id = element.getAsString();
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("item")) {
                id = object.get("item").getAsString();
            } else if (object.has("id")) {
                id = object.get("id").getAsString();
            }
        }
        if (id == null || id.startsWith("#")) {
            return ItemStack.EMPTY;   // tags are handled by stacks(), which expands them
        }
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(parsed);
        // An id that resolves to nothing comes back as AIR, not null, so this fails as a content bug
        // rather than an NPE - the same trap PrinterTests.upstreamOf records.
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }
}
