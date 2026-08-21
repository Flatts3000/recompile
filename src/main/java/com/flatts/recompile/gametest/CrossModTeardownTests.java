package com.flatts.recompile.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * Teardown routes for AE2 and Ender IO (#275): a player with a chest of superseded conduits or spare
 * cables can reclaim the material, which is the one thing this mod is about.
 *
 * <p><b>What CI can and cannot say.</b> Neither mod is present here, so nothing in this file can prove
 * a teardown produces anything. What it proves is the shape the other cross-mod stopgaps use: the
 * recipes are inert, and <b>absent for the right reason</b>. That distinction is the whole test -
 * "no ae2 recipe is loaded" passes identically whether the guard is doing its job or the file is
 * failing to parse on its own ids, and the second is a silent ERROR line in a green run.
 */
final class CrossModTeardownTests {

    private CrossModTeardownTests() {
    }

    /** Every file this feature ships, and the mod each one is guarded on. */
    private static final Map<String, String> RECIPES = Map.of(
        "teardown_ae2_glass_cable", "ae2",
        "teardown_ae2_covered_cable", "ae2",
        "teardown_ae2_smart_cable", "ae2",
        "teardown_ae2_covered_dense_cable", "ae2",
        "teardown_ae2_smart_dense_cable", "ae2",
        "teardown_enderio_conduit", "enderio",
        "teardown_enderio_basic_capacitor", "enderio",
        "teardown_enderio_iron_gear", "enderio");

    static void register() {

        RCGameTests.test("cross_mod_teardowns_are_inert_for_the_right_reason", 40, helper -> {
            List<String> unguarded = new ArrayList<>();
            List<String> wrongStation = new ArrayList<>();
            var badIds = new TreeSet<String>();
            int present = 0;

            for (Map.Entry<String, String> entry : RECIPES.entrySet()) {
                String body = read("/data/recompile/recipe/" + entry.getKey() + ".json");
                if (body == null) {
                    continue;
                }
                present++;
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();

                // THE GUARD, and that it names the RIGHT mod - the check below requires the modid to
                // equal the one this file's items come from, so a file producing ae2 items guarded on
                // enderio counts as unguarded rather than as a separate finding.
                boolean guarded = false;
                if (root.has("neoforge:conditions") && root.get("neoforge:conditions").isJsonArray()) {
                    for (JsonElement raw : root.getAsJsonArray("neoforge:conditions")) {
                        if (!raw.isJsonObject()) {
                            continue;
                        }
                        JsonObject condition = raw.getAsJsonObject();
                        guarded |= condition.has("type") && condition.has("modid")
                            && "neoforge:mod_loaded".equals(condition.get("type").getAsString())
                            && entry.getValue().equals(condition.get("modid").getAsString());
                    }
                }
                if (!guarded) {
                    unguarded.add(entry.getKey());
                }

                // EVERY ID THIS FILE NAMES THAT IS NOT THE GUARDED MOD'S MUST RESOLVE. A typo in a
                // recompile: or minecraft: id cannot be caught by the guard - the file would load
                // correctly for a player who HAS the mod and then fail on an item that does not
                // exist. Foreign ids are skipped because they legitimately do not resolve here.
                collectIds(root, entry.getValue(), entry.getKey(), badIds);

                // The station, which is not a registry id and so cannot be checked above. The
                // Workbench only runs recipes whose station equals TeardownRecipe.DEFAULT_STATION,
                // so anything else here parses cleanly and is simply never reachable.
                String station = root.has("station") ? root.get("station").getAsString()
                    : com.flatts.recompile.content.recipe.TeardownRecipe.DEFAULT_STATION;
                if (!com.flatts.recompile.content.recipe.TeardownRecipe.DEFAULT_STATION
                        .equals(station)) {
                    wrongStation.add(entry.getKey() + " has station '" + station
                        + "', which no station in this mod reads");
                }
            }

            // NOTHING LOADED, which is the outcome - but it is asserted last because on its own it
            // cannot tell a working guard from a broken file.
            List<String> loaded = new ArrayList<>();
            for (var holder : helper.getLevel().recipeAccess().recipeMap().values()) {
                String path = holder.id().identifier().getPath();
                if (RECIPES.containsKey(path)) {
                    loaded.add(path);
                }
            }

            helper.assertTrue(present == RECIPES.size(),
                "found " + present + " of the " + RECIPES.size() + " cross-mod teardown files. "
                    + "Partial is the bad state: these ship and leave as one unit.");
            helper.assertTrue(unguarded.isEmpty(),
                "these produce another mod's item with no neoforge:mod_loaded condition naming that "
                    + "mod, so without it they do not merely fail to apply - they FAIL TO PARSE on "
                    + "their own ids, which is one ERROR line in an otherwise green run: " + unguarded);
            helper.assertTrue(wrongStation.isEmpty(),
                "these are unreachable despite parsing correctly: " + wrongStation);
            helper.assertTrue(badIds.isEmpty(),
                "these ids do not resolve, and the guard cannot save them - the file would load fine "
                    + "for a player who has the mod and then name an item that does not exist: "
                    + badIds);

            // WITH A MOD PRESENT the expectation inverts: its recipes must actually be there. This
            // branch only ever runs by hand, by dropping the jars into run/mods - CI has neither -
            // but it is here rather than in a throwaway probe so the with-mod check is repeatable.
            var expected = new TreeSet<String>();
            for (Map.Entry<String, String> entry : RECIPES.entrySet()) {
                if (net.neoforged.fml.ModList.get().isLoaded(entry.getValue())) {
                    expected.add(entry.getKey());
                }
            }
            if (!expected.isEmpty()) {
                var missing = new TreeSet<>(expected);
                loaded.forEach(missing::remove);
                helper.assertTrue(missing.isEmpty(),
                    "these teardowns did NOT load even though their mod is installed, so a player "
                        + "who has the mod cannot reclaim the material: " + missing);
            }

            var unexpected = new ArrayList<>(loaded);
            unexpected.removeAll(expected);
            helper.assertTrue(unexpected.isEmpty(),
                "cross-mod teardowns loaded without their mod present: " + unexpected);
            helper.succeed();
        });
    }

    /** Every non-foreign item id anywhere in the file, checked against the registry. */
    private static void collectIds(JsonElement node, String foreignNs, String file,
            TreeSet<String> bad) {
        if (node.isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray()) {
                collectIds(child, foreignNs, file, bad);
            }
            return;
        }
        if (node.isJsonObject()) {
            for (var entry : node.getAsJsonObject().entrySet()) {
                // "type" is a recipe-type id and "station" is a plain string key compared against
                // TeardownRecipe.DEFAULT_STATION - neither is an item or block, so neither resolves
                // against a registry. The station is asserted separately instead, because a typo
                // there leaves a recipe that parses, loads, and no station will ever run.
                // "recipe" inside a teaches entry is a RECIPE id, not an item - resolving it against
                // the item registry would report a correct file as broken. Latent today (none of
                // these teach anything) and caught in review of #283 precisely because this guard is
                // meant to survive future edits.
                if ("_comment".equals(entry.getKey()) || "neoforge:conditions".equals(entry.getKey())
                    || "type".equals(entry.getKey()) || "station".equals(entry.getKey())
                    || "recipe".equals(entry.getKey())) {
                    continue;
                }
                collectIds(entry.getValue(), foreignNs, file, bad);
            }
            return;
        }
        if (!node.isJsonPrimitive() || !node.getAsJsonPrimitive().isString()) {
            return;
        }
        String value = node.getAsString();
        // Tags do not resolve at parse time and are inert when absent, which is why the cable inputs
        // are tags in the first place; station and tool ids are checked like any other.
        if (value.startsWith("#") || !value.contains(":") || value.contains(" ")) {
            return;
        }
        String namespace = value.substring(0, value.indexOf(':'));
        if (foreignNs.equals(namespace) || "recompile".equals(namespace)) {
            // recompile: ids include block and item ids alike, so check both registries.
            if ("recompile".equals(namespace)) {
                Identifier id = Identifier.tryParse(value);
                boolean known = id != null
                    && (BuiltInRegistries.ITEM.getValue(id) != Items.AIR
                        || BuiltInRegistries.BLOCK.containsKey(id));
                if (!known) {
                    bad.add(file + " -> " + value);
                }
            }
            return;
        }
        if (!"minecraft".equals(namespace)) {
            return;
        }
        Identifier id = Identifier.tryParse(value);
        if (id == null || BuiltInRegistries.ITEM.getValue(id) == Items.AIR) {
            bad.add(file + " -> " + value);
        }
    }

    /** One bundled JSON as text, or null. */
    private static String read(String path) {
        try (java.io.InputStream in = CrossModTeardownTests.class.getResourceAsStream(path)) {
            return in == null ? null
                : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failed) {
            return null;
        }
    }
}
