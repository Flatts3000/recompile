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

    /** The demolition yard's stream: stone shards rather than household scrap. */
    public static final String RUBBLE = "/data/recompile/loot_table/gameplay/rubble_pulls.json";
    /** Bulky Waste's find table (broken open with the prybar). */
    public static final String BULKY = "/data/recompile/loot_table/blocks/bulky_waste.json";

    /** What a Cutting Torch gets out of a Steel I-Beam. */
    public static final String STEEL_BEAM = "/data/recompile/loot_table/blocks/steel_i_beam.json";

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
                JsonObject pool = poolEl.getAsJsonObject();
                // A pool can be gated behind a chance condition, and ignoring it overstates every entry
                // inside. Bulky Waste's painting pool is 7%, so its six paintings are ~1.2% each - JEI
                // would have shown 16.7%, a 14x lie, and the same shape of wrong as telling players the
                // Burn Barrel smelts iron. The odds ARE the information in these categories.
                float poolChance = poolChance(pool);
                JsonArray entries = pool.getAsJsonArray("entries");
                int total = 0;
                for (JsonElement e : entries) {
                    // Count EVERY entry's weight, including minecraft:empty. A rare-bonus pool (a big
                    // empty weight + one rare item) has to divide by the full weight, or the item reads
                    // as 100% of its pool instead of its true 1-in-N odds.
                    total += weight(e.getAsJsonObject());
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
                        out.add(new Weighted(new ItemStack(item), poolChance * weight(o) / total));
                    }
                }
            }
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    /**
     * A pool's own probability of rolling at all, from a {@code minecraft:random_chance} condition.
     *
     * <p>Returns 1 when the pool is unconditional. Only this one condition is understood on purpose:
     * anything else (a tool check, an enchantment) is not a probability and cannot honestly be folded
     * into a percentage, so a pool carrying one is reported at its raw weight rather than guessed at.
     */
    private static float poolChance(JsonObject pool) {
        if (!pool.has("conditions")) {
            return 1.0F;
        }
        float chance = 1.0F;
        for (JsonElement c : pool.getAsJsonArray("conditions")) {
            JsonObject o = c.getAsJsonObject();
            if (o.has("condition") && "minecraft:random_chance".equals(o.get("condition").getAsString())
                    && o.has("chance")) {
                chance *= o.get("chance").getAsFloat();
            }
        }
        return chance;
    }

    private static boolean isItem(JsonObject entry) {
        return entry.has("type") && "minecraft:item".equals(entry.get("type").getAsString());
    }

    private static int weight(JsonObject entry) {
        return entry.has("weight") ? entry.get("weight").getAsInt() : 1;
    }
}
