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
 * The bundled {@code recompile:pulverizing} recipes, read for the viewers (#189).
 *
 * <p>Reads files rather than the recipe manager for the reason {@link RecipeFiles} states: recipes are
 * not client-synced in 26.1 and the manager is reliably empty when JEI asks. Discovered by walking the
 * recipe folder rather than from a list of paths, which is the mistake {@code TeardownData} already made
 * once - it named two recipes, a third shipped, and the viewers denied it existed.
 *
 * <p><b>The input carries its count</b>, the way {@code SeparatingData} does, because {@code count} is
 * this type's ratio dial. A category drawing a single item would be describing a different machine.
 *
 * <p><b>One output, and that is the type's whole shape.</b> Separating yields several distinct things
 * plus byproducts; pulverizing yields one finer thing. This reads {@code result} rather than
 * {@code results} and has no byproduct pass, which is the schema difference made visible.
 */
public final class PulverizingData {

    /**
     * One row: the feed with its real count, the powder it becomes, and what it costs.
     *
     * <p>{@code inputs} is a LIST because an ingredient may be a tag - the sherd recipe takes all 23 at
     * once. JEI cycles a multi-item input natively, so the row shows what it really accepts rather than
     * one arbitrary member.
     */
    public record Entry(List<ItemStack> inputs, List<SortingData.Weighted> outputs, int ticks,
                        int energy) {

        /** The first accepted item, for callers that can only draw one. */
        public ItemStack input() {
            return inputs.isEmpty() ? ItemStack.EMPTY : inputs.get(0);
        }
    }

    private static List<Entry> cached;

    private PulverizingData() {
    }

    public static synchronized List<Entry> all() {
        if (cached != null) {
            return cached;
        }
        List<Entry> out = new ArrayList<>();
        for (JsonObject recipe : RecipeFiles.ofType("recompile:pulverizing")) {
            int count = recipe.has("count") ? recipe.get("count").getAsInt() : 1;
            List<ItemStack> inputs = stacks(recipe.get("input"), count);
            if (inputs.isEmpty()) {
                continue;
            }
            ItemStack result = ItemStack.EMPTY;
            if (recipe.has("result") && recipe.get("result").isJsonObject()) {
                JsonObject entry = recipe.getAsJsonObject("result");
                result = stack(entry.get("item"),
                    entry.has("count") ? entry.get("count").getAsInt() : 1);
            }
            if (result.isEmpty()) {
                continue;
            }
            // Chance 1: a mill does not roll. Everything that goes in comes out as the same powder,
            // which is why an odds column beside every row would be noise.
            out.add(new Entry(inputs, List.of(new SortingData.Weighted(result, 1.0F)),
                recipe.has("ticks") ? recipe.get("ticks").getAsInt() : 60,
                recipe.has("energy") ? recipe.get("energy").getAsInt() : 24));
        }
        cached = List.copyOf(out);
        return cached;
    }

    /**
     * Every item an ingredient accepts, as stacks of the given size.
     *
     * <p><b>A TAG EXPANDS TO ITS MEMBERS</b> rather than being dropped. Returning nothing for a tag is
     * what {@code SeparatingData} does and it was fine there, because no separating recipe uses one -
     * the moment the sherd recipe shipped, the first tag-input pulverizing recipe silently vanished
     * from JEI. A player holding a sherd was told nothing crushes it while bone and E-Scrap both
     * showed, which is the "a mechanic nobody can see does not exist" failure the plugin warns about.
     */
    private static List<ItemStack> stacks(JsonElement element, int count) {
        if (element != null && element.isJsonPrimitive()
                && element.getAsString().startsWith("#")) {
            String path = element.getAsString().substring(1);
            Identifier parsed = Identifier.tryParse(path);
            if (parsed == null) {
                return List.of();
            }
            var key = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, parsed);
            List<ItemStack> out = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (new ItemStack(item).is(key)) {
                    out.add(new ItemStack(item, count));
                }
            }
            return List.copyOf(out);
        }
        ItemStack single = stack(element, count);
        return single.isEmpty() ? List.of() : List.of(single);
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
            return ItemStack.EMPTY;   // tags are handled by stacks(), which expands them
        }
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.getValue(parsed);
        // An id that resolves to nothing comes back as AIR, not null, so this fails as a content bug
        // rather than an NPE - the same trap PrinterTests.upstreamOf records.
        return item == net.minecraft.world.item.Items.AIR
            ? ItemStack.EMPTY : new ItemStack(item, count);
    }
}
