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

/**
 * Reads what a pull / find loot table can yield, for the JEI categories. Server-safe (no
 * client or JEI types), so it can be unit-tested by a GameTest.
 *
 * <p><b>Why parse the bundled JSON</b> instead of sampling the live table: loot tables
 * are not synced to clients, so a client-side JEI plugin cannot read the loaded table on a
 * dedicated server. The bundled default is deterministic, works everywhere, and needs no
 * server. The trade-off is that a datapack that retunes a pull table is not reflected in
 * JEI - acceptable for the mod's own tables; revisit if pack-tuned pulls need to show.
 *
 * <p>These tables are flat (one pool of weighted {@code minecraft:item} entries), so the
 * chance is simply the entry weight over the pool total. {@code set_count} functions
 * (e.g. scrap metal 1-2) are ignored for display - the item and its odds are the point.
 */
public final class SortingData {

    /** The household pull stream: garbage blocks and compacted bales draw from it. */
    public static final String HOUSEHOLD = "/data/recompile/loot_table/gameplay/household_pulls.json";
    /** The trash-bag pull stream. */
    public static final String BAG = "/data/recompile/loot_table/gameplay/bag_pulls.json";
    /** Bulky Waste's find table (broken open with the prybar). */
    public static final String BULKY = "/data/recompile/loot_table/blocks/bulky_waste.json";

    /** One possible output and how likely it is (0..1). */
    public record Weighted(ItemStack stack, float chance) {}

    private SortingData() {
    }

    /** Weighted item outputs of a bundled loot table, or empty if it cannot be read. */
    public static List<Weighted> outputs(String resourcePath) {
        try (InputStream in = SortingData.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return List.of();
            }
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Weighted> out = new ArrayList<>();
            for (JsonElement poolEl : root.getAsJsonArray("pools")) {
                JsonArray entries = poolEl.getAsJsonObject().getAsJsonArray("entries");
                int total = 0;
                for (JsonElement e : entries) {
                    JsonObject o = e.getAsJsonObject();
                    if (isItem(o)) {
                        total += weight(o);
                    }
                }
                if (total == 0) {
                    continue;
                }
                for (JsonElement e : entries) {
                    JsonObject o = e.getAsJsonObject();
                    if (!isItem(o)) {
                        continue;
                    }
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(o.get("name").getAsString()));
                    if (item != Items.AIR) {
                        out.add(new Weighted(new ItemStack(item), (float) weight(o) / total));
                    }
                }
            }
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static boolean isItem(JsonObject entry) {
        return entry.has("type") && "minecraft:item".equals(entry.get("type").getAsString());
    }

    private static int weight(JsonObject entry) {
        return entry.has("weight") ? entry.get("weight").getAsInt() : 1;
    }
}
