package com.flatts.recompile.compat;

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

    /** One teardown recipe as JEI needs it: the input, its outputs, and the required tool (or null). */
    public record Entry(ItemStack input, List<SortingData.Weighted> outputs, @Nullable Item tool) {}

    /** Every bundled teardown recipe surfaced to viewers (hardcoded, like SortingData's paths). */
    private static final List<String> ALL_PATHS = List.of(MATTRESS);
    private static List<Entry> cached;

    private TeardownData() {
    }

    /** All readable bundled teardown recipes, parsed once and cached. */
    public static List<Entry> all() {
        if (cached == null) {
            List<Entry> entries = new ArrayList<>();
            for (String path : ALL_PATHS) {
                Entry entry = read(path);
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

    /** Read a bundled teardown recipe, or null if it cannot be read or its input is not a bare item. */
    public static @Nullable Entry read(String resourcePath) {
        try (InputStream in = TeardownData.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

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
