package com.flatts.recompile.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.flatts.recompile.RCConfig;
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
 * What the Cupola Furnace does, for JEI - which is a blasting recipe <em>plus slag</em> (#243).
 *
 * <p><b>The Cupola is still a {@code RecipeType.BLASTING} machine and that has not changed.</b> It is
 * the iron gate: a vanilla furnace cannot run a blasting recipe at all, and a vanilla blast furnace
 * costs five iron ingots, so it is circular and unreachable before iron. The gate is a property of the
 * machine rather than an absence of materials, which is the lesson #91 cost two designs.
 *
 * <p><b>So why a category of its own.</b> Because vanilla's blasting display has ONE result slot, and
 * this machine hands back two things. Every eighth completed smelt also rakes off a lump of slag, and
 * that is the entire input to the Separator, the Pulverizer and the Slag Furnace - the whole obsidian
 * chain hangs off a byproduct that JEI structurally could not draw. A player reading "Blasting" was
 * told the Cupola turns Circuit Powder into a gold nugget and nothing else, which is true and
 * materially incomplete.
 *
 * <p>It is the same shape as the reason the machine needed a bespoke MENU (#240): vanilla cooking has
 * one result because vanilla cooking recipes have one result, and a machine with a byproduct is
 * outside what those screens and categories were built to show.
 *
 * <p><b>And the Cupola is not a catalyst on vanilla Blasting any more</b>, so these rows are the only
 * place it appears. Listing it in both showed each recipe twice with the Cupola named in both, where
 * the second entry was a superset of the first - noise rather than a second fact.
 *
 * <p><b>The slag is COUNTED, not rolled</b>, so it must never be drawn as a percentage. One per N
 * smelts exactly, N from config - not an N-to-one chance per smelt, which would be a different
 * machine that sometimes gives you nothing for sixteen smelts running. The category says "1 every N"
 * for that reason.
 *
 * <p><b>And the count is per SMELT, not per recipe.</b> {@code rakeSlag} increments off the result
 * slot growing, so iron, copper and gold all count toward the same tally - which is why every row here
 * carries the same slag note rather than only the iron ones.
 */
public final class CupolaData {

    /** One row: what goes in, what comes out, and how long it takes. */
    public record Entry(List<ItemStack> inputs, ItemStack output, int ticks, float experience) {

        /** The first accepted item, for callers that can only draw one. */
        public ItemStack input() {
            return inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0);
        }
    }

    /** Vanilla's default when a blasting recipe omits {@code cookingtime}. */
    private static final int DEFAULT_BLASTING_TICKS = 100;

    private static List<Entry> cached;

    private CupolaData() {
    }

    /**
     * How many smelts buy one lump of slag.
     *
     * <p>Read from config rather than written as 8, because it IS config - a pack that retunes it
     * would otherwise have JEI confidently teaching the wrong number, which is worse than JEI saying
     * nothing.
     */
    public static int smeltsPerSlag() {
        return RCConfig.CUPOLA_SMELTS_PER_SLAG.get();
    }

    public static synchronized List<Entry> all() {
        if (cached != null) {
            return cached;
        }
        List<Entry> out = new ArrayList<>();
        for (JsonObject recipe : RecipeFiles.ofType("minecraft:blasting")) {
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
            out.add(new Entry(inputs, result,
                recipe.has("cookingtime") ? recipe.get("cookingtime").getAsInt()
                    : DEFAULT_BLASTING_TICKS,
                recipe.has("experience") ? recipe.get("experience").getAsFloat() : 0.0F));
        }
        cached = List.copyOf(out);
        return cached;
    }

    /**
     * Every item an ingredient accepts.
     *
     * <p>Three forms, because 26.1's {@code Ingredient} has three and dropping any of them loses the
     * whole recipe rather than part of it: a {@code "#tag"} string expands to its members, a bare id or
     * an {@code item} object is one stack, and a JSON ARRAY is the union of its entries. The array form
     * was missed at first, and it fails in the worst available way - {@code all()} sees an empty input
     * list and skips the recipe entirely, so a pack extending this public schema gets a recipe that
     * works in-world and does not exist in JEI, with nothing logged. The bundled recipes use none of
     * it, which is exactly why a test would not have caught it either.
     */
    private static List<ItemStack> stacks(JsonElement element) {
        if (element != null && element.isJsonArray()) {
            List<ItemStack> out = new ArrayList<>();
            for (JsonElement inner : element.getAsJsonArray()) {
                out.addAll(stacks(inner));
            }
            return List.copyOf(out);
        }
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

    /** An item id as a stack. The caller unwraps {@code result.id} before calling in. */
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
