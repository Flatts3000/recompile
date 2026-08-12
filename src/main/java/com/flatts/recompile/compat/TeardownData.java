package com.flatts.recompile.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Reads a bundled {@code recompile:teardown} recipe for the JEI Teardown category. Server-safe
 * (no client / JEI types), so a GameTest can unit-test it.
 *
 * <p><b>Why parse the bundled JSON</b> instead of the live recipe: unlike 1.20, MC 26.1 does not
 * sync the full recipe set to clients (the client's {@code RecipeAccess} exposes only property
 * sets and stonecutter recipes), so a client-side JEI plugin cannot read a teardown recipe off a
 * dedicated server. The bundled default is deterministic and works everywhere. Same trade-off as
 * {@link SortingData}: a datapack that adds teardown recipes is not reflected in JEI - acceptable
 * for the mod's own recipes; revisit if pack-added teardowns need to show.
 *
 * <p>Reuses {@link SortingData.Weighted} for output rows: {@code results} are certain (chance 1.0,
 * carrying their count) and {@code extras} carry their own chance. The required {@code tool} is
 * read for callers that want it; the input handles only a bare item id (the schema also allows a
 * tag or array, surfaced later if a real recipe needs it).
 */
public final class TeardownData {

    /** The mattress teardown - the one real find today (string + fiber + springs-as-scrap). */
    public static final String MATTRESS = "/data/recompile/recipe/mattress.json";
    public static final String WASHING_MACHINE = "/data/recompile/recipe/washing_machine.json";

    /** One teardown recipe as JEI needs it: the input, its outputs, and the required tool (or null). */
    public record Entry(ItemStack input, List<SortingData.Weighted> outputs, @Nullable Item tool) {}

    // The hardcoded path list that used to live here is gone. It named two recipes; when a third
    // shipped - the Broken Hydroponics Bay - it was invisible to every viewer, so the item could be
    // torn down in-world while JEI denied the teardown existed. A second inventory of the same facts
    // is always the copy nobody remembers to update. RecipeFiles discovers them instead.
    private static List<Entry> cached;

    private TeardownData() {
    }

    /** All readable bundled teardown recipes, parsed once and cached. */
    public static List<Entry> all() {
        if (cached == null) {
            List<Entry> entries = new ArrayList<>();
            for (com.google.gson.JsonObject recipe : RecipeFiles.ofType("recompile:teardown")) {
                Entry entry = parse(recipe);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            cached = List.copyOf(entries);
        }
        return cached;
    }

    /** The teardown for a given input item, or null if none (bundled recipes only). */
    public static @Nullable Entry forInput(Item input) {
        for (Entry entry : all()) {
            if (entry.input().is(input)) {
                return entry;
            }
        }
        return null;
    }

    /** Read a bundled teardown recipe by path, or null if it cannot be read. Kept for tests. */
    public static @Nullable Entry read(String resourcePath) {
        try (InputStream in = TeardownData.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            return parse(JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            return null;
        }
    }

    /** One already-parsed teardown recipe, or null if its input is not a bare item. */
    public static @Nullable Entry parse(JsonObject root) {
        try {
            JsonElement inputEl = root.get("input");
            if (inputEl == null || !inputEl.isJsonPrimitive()) {
                return null; // tag / array inputs are not surfaced yet
            }
            Item input = item(inputEl.getAsString());
            if (input == Items.AIR) {
                return null;
            }

            List<SortingData.Weighted> outputs = new ArrayList<>();
            if (root.has("results")) {
                for (JsonElement el : root.getAsJsonArray("results")) {
                    JsonObject o = el.getAsJsonObject();
                    Item item = item(o.get("item").getAsString());
                    if (item != Items.AIR) {
                        int count = o.has("count") ? o.get("count").getAsInt() : 1;
                        outputs.add(new SortingData.Weighted(new ItemStack(item, count), 1.0f));
                    }
                }
            }
            // What it TEACHES is an output too (#95). A mattress teardown hands back an Idea Fragment
            // as reliably as it hands back string, and a category that lists the string but not the
            // fragment tells a player the knowledge came from somewhere else - which is the one thing
            // about this mechanic that is hard to work out by playing.
            if (root.has("teaches")) {
                for (JsonElement el : root.getAsJsonArray("teaches")) {
                    JsonObject o = el.getAsJsonObject();
                    Identifier set = Identifier.parse(o.get("recipe").getAsString());
                    float chance = o.has("chance") ? o.get("chance").getAsFloat() : 0.0f;
                    if (chance <= 0.0f) {
                        continue;
                    }
                    outputs.add(new SortingData.Weighted(
                        com.flatts.recompile.content.item.IdeaFragmentItem.of(
                            com.flatts.recompile.registry.RCItems.IDEA_FRAGMENT.get(), set, 1),
                        chance));
                }
            }
            // POOLS ARE OUTPUTS TOO, and a viewer that skipped them would show a teardown missing
            // most of what it gives. That is the failure mode #180 was: a mechanic working in-world
            // while every viewer quietly denied it. Weight over pool total is the real per-draw
            // chance, times rolls, which is what a player wants to read.
            if (root.has("pools")) {
                for (JsonElement poolEl : root.getAsJsonArray("pools")) {
                    JsonObject pool = poolEl.getAsJsonObject();
                    int rolls = pool.has("rolls") ? pool.get("rolls").getAsInt() : 1;
                    JsonArray entries = pool.getAsJsonArray("entries");
                    float total = 0.0f;
                    for (JsonElement e : entries) {
                        JsonObject o = e.getAsJsonObject();
                        total += o.has("weight") ? o.get("weight").getAsFloat() : 1.0f;
                    }
                    if (total <= 0.0f) {
                        continue;
                    }
                    boolean teaches = pool.has("teaches") && pool.get("teaches").getAsBoolean();
                    for (JsonElement e : entries) {
                        JsonObject o = e.getAsJsonObject();
                        if (!o.has("item")) {
                            continue;   // the filler slot has nothing to show
                        }
                        Item item = item(o.get("item").getAsString());
                        if (item == Items.AIR) {
                            continue;
                        }
                        float weight = o.has("weight") ? o.get("weight").getAsFloat() : 1.0f;
                        int count = o.has("count") ? o.get("count").getAsInt() : 1;
                        float chance = Math.min(1.0f, weight / total * rolls);
                        outputs.add(new SortingData.Weighted(new ItemStack(item, count), chance));
                        if (teaches) {
                            // A teaching pool hands over the fragment for whatever it drew, so the
                            // fragment is exactly as likely as the component beside it.
                            outputs.add(new SortingData.Weighted(
                                com.flatts.recompile.content.item.IdeaFragmentItem.of(
                                    com.flatts.recompile.registry.RCItems.IDEA_FRAGMENT.get(),
                                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item),
                                    1),
                                chance));
                        }
                    }
                }
            }
            if (root.has("extras")) {
                for (JsonElement el : root.getAsJsonArray("extras")) {
                    JsonObject o = el.getAsJsonObject();
                    Item item = item(o.get("item").getAsString());
                    if (item != Items.AIR) {
                        outputs.add(new SortingData.Weighted(new ItemStack(item), o.get("chance").getAsFloat()));
                    }
                }
            }

            Item tool = root.has("tool") && root.get("tool").isJsonPrimitive()
                ? item(root.get("tool").getAsString()) : null;
            return new Entry(new ItemStack(input), outputs, tool == Items.AIR ? null : tool);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }
}
