package com.flatts.recompile.gametest;

import com.flatts.recompile.Recompile;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * A shipped recipe must be reachable at a bench, and having a recipe object is not the same thing.
 *
 * <p><b>Two recipes that accept the same grid are one recipe.</b> A crafting grid resolves to a single
 * result, so when two recipes match the same arrangement of the same items only one of them can ever
 * be crafted - and the loser fails in the quietest way this mod has: no error, no log line, a JEI page
 * that says it works, and a player who follows it and gets the other thing.
 *
 * <p>It has shipped twice. {@code trommel} and {@code pulverizer} were byte-identical from #197 until
 * this test was written, so one of the two machines was uncraftable in survival for four releases -
 * they were built by copying each other, which is the same way their Jade coverage came apart in
 * {@code MachineParityTests}. The second was caught in review before merge (#267): sculk powder made
 * both {@code minecraft:sculk} and {@code minecraft:sculk_vein} from one loose powder.
 *
 * <p><b>Why it asks the live recipe manager rather than comparing JSON.</b> A static comparison has to
 * reimplement vanilla's matcher to be right - shaped recipes are distinguished by their PATTERN, so
 * stairs and a wall built from six of one block are not a collision, while a shapeless recipe swallows
 * every arrangement of its multiset and so CAN collide with a shaped one. Getting that wrong in either
 * direction is worse than no test: the permissive version misses real collisions and the strict version
 * cries wolf on every stairs/wall pair in the repo. {@code getRecipesFor} is the matcher itself, and it
 * returns ALL matches rather than the first, which is the whole reason this can see the shadowed half.
 */
final class RecipeReachabilityTests {

    private RecipeReachabilityTests() {
    }

    static void register() {

        RCGameTests.test("every_crafting_recipe_is_reachable_at_a_bench", 60, helper -> {
            var level = helper.getLevel();
            var recipeMap = level.recipeAccess().recipeMap();
            Map<String, Item> tagPick = new HashMap<>();
            List<String> shadowed = new ArrayList<>();
            List<String> unreadable = new ArrayList<>();
            List<String> notItself = new ArrayList<>();
            int checked = 0;

            for (var holder : recipeMap.values()) {
                var rid = holder.id().identifier();
                String body = read("/data/" + rid.getNamespace() + "/recipe/" + rid.getPath() + ".json");
                if (body == null) {
                    // Vanilla's own recipes are not on this classpath; ours always are.
                    if (Recompile.MOD_ID.equals(rid.getNamespace())) {
                        unreadable.add(rid.toString());
                    }
                    continue;
                }

                JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                String type = root.has("type") ? root.get("type").getAsString() : "";
                CraftingInput input;
                if ("minecraft:crafting_shapeless".equals(type)) {
                    List<ItemStack> items = new ArrayList<>();
                    for (JsonElement e : root.getAsJsonArray("ingredients")) {
                        items.add(new ItemStack(resolve(e, tagPick)));
                    }
                    int w = Math.min(3, items.size());
                    int h = (items.size() + w - 1) / w;
                    while (items.size() < w * h) {
                        items.add(ItemStack.EMPTY);
                    }
                    input = CraftingInput.of(w, h, items);
                } else if ("minecraft:crafting_shaped".equals(type)) {
                    List<String> pattern = new ArrayList<>();
                    for (JsonElement e : root.getAsJsonArray("pattern")) {
                        pattern.add(e.getAsString());
                    }
                    JsonObject key = root.getAsJsonObject("key");
                    int w = 0;
                    for (String row : pattern) {
                        w = Math.max(w, row.length());
                    }
                    List<ItemStack> items = new ArrayList<>();
                    for (String row : pattern) {
                        for (int i = 0; i < w; i++) {
                            char c = i < row.length() ? row.charAt(i) : ' ';
                            items.add(c == ' ' ? ItemStack.EMPTY
                                : new ItemStack(resolve(key.get(String.valueOf(c)), tagPick)));
                        }
                    }
                    input = CraftingInput.of(w, pattern.size(), items);
                } else {
                    // Not a grid recipe: smelting, teardown, separating, blueprint crafting.
                    continue;
                }

                checked++;
                var matches = new TreeSet<String>();
                recipeMap.getRecipesFor(RecipeType.CRAFTING, input, level)
                    .forEach(m -> matches.add(m.id().identifier().toString()));

                // A recipe that does not match its OWN grid means this test built the grid wrong, and
                // every collision it reports below would be measured against nothing. Loud, not silent.
                if (!matches.remove(rid.toString())) {
                    notItself.add(rid.toString());
                } else if (!matches.isEmpty()) {
                    shadowed.add(rid + " <-> " + matches);
                }
            }

            helper.assertTrue(unreadable.isEmpty(),
                "these recipes are loaded but their JSON is not on the classpath, so the sweep skipped "
                    + "them: " + unreadable);
            helper.assertTrue(notItself.isEmpty(),
                "these recipes do not match the grid this test built for them, so the collision check "
                    + "below is measuring nothing for them: " + notItself);
            helper.assertTrue(checked >= 100,
                "only " + checked + " grid recipes were swept - discovery is broken, so this would pass "
                    + "against a repo full of collisions");
            helper.assertTrue(shadowed.isEmpty(),
                "these recipes accept the same grid, so only one of each pair can ever be crafted and "
                    + "the other is dead content: " + shadowed);
            helper.succeed();
        });

        // A GUARDED OVERRIDE FOR A MOD THAT IS NOT HERE MUST BE INERT, AND FOR THE RIGHT REASON (#269).
        //
        // This mod ships four files at Simple Magnets' own recipe ids, re-theming its magnets onto
        // Magnet Scrap. An override lives at ANOTHER mod's resource location, so without that mod the
        // ids inside it - simplemagnets:basicmagnet as a result - resolve to nothing.
        //
        // <p><b>The obvious test is vacuous, which is why this one is shaped oddly.</b> "No
        // simplemagnets recipe is loaded" passes in BOTH the good and the bad state: with the guard
        // the recipe is skipped, and WITHOUT the guard it fails to parse - measured, the log reads
        // {@code Unknown registry key ... simplemagnets:basicmagnet} - and is equally absent. A broken
        // file and a correctly guarded one are indistinguishable from the recipe map. So absence is
        // checked together with the REASON for it: the file on disk must carry the guard.
        //
        // <p>A parse failure here is quieter than the loot-table equivalent. One bad recipe file does
        // not take its neighbours with it, so the run stays green and the only trace is a single ERROR
        // line - which is how the seventeen-recipe incident stayed hidden.
        //
        // <p><b>The guard is checked by its CONTENTS, not by the key being there</b>, because two
        // states get past a bare presence check and both are the failure this exists to catch. An
        // EMPTY condition array reads as all-conditions-met, so the recipe decodes, dies on its own
        // result id, and leaves nothing loaded - green on both counts. And a typo in the modid makes
        // the condition simply false: green here forever, and when the pack DOES load Simple Magnets
        // the override is skipped and the stock ender-pearl recipe stays, with nothing anywhere
        // saying so.
        //
        // <p><b>The ids are resolved too, and that is the half CI could otherwise never reach.</b>
        // These files are never decoded in a test run - the guard sees to that - so the guard that
        // makes CI safe is also what leaves the four files completely unverified. A typo in an
        // ingredient does not degrade to the stock recipe: recipes load through
        // SimpleJsonResourceReloadListener, which reads only the TOP resource at each path, and this
        // mod is ordered AFTER, so ours is the only file read for that id. It fails to parse, the id
        // is then absent entirely, and the magnet is UNCRAFTABLE rather than merely stock-themed -
        // strictly worse than the dead-duplicate case, with one ERROR line as the only trace.
        //
        // <p><b>It does not pin the files.</b> Each is checked only if it is present, so when this
        // stopgap leaves (Flatts3000/trashlands#47) deleting the four files is the whole removal and
        // this passes over an empty list. Naming them is forced rather than chosen: NeoForge's dev
        // classpath resolves an individual resource but will not enumerate a directory, which
        // SewerLootTests found the hard way.
        RCGameTests.test("a_guarded_override_is_inert_without_its_mod", 40, helper -> {
            var level = helper.getLevel();
            if (net.neoforged.fml.ModList.get().isLoaded("simplemagnets")) {
                // Then the overrides are SUPPOSED to be live and every assertion below is backwards.
                // Whether the override won its load-order race is an in-game check, not one CI makes.
                helper.succeed();
                return;
            }

            // Every simplemagnets: id these four files may name, checked against the jar the pack
            // ships. Anything else in that namespace is a typo, and a typo here is silent.
            var known = java.util.Set.of("simplemagnets:basicmagnet", "simplemagnets:advancedmagnet",
                "simplemagnets:basic_demagnetization_coil",
                "simplemagnets:advanced_demagnetization_coil");

            List<String> unguarded = new ArrayList<>();
            List<String> loaded = new ArrayList<>();
            List<String> badIds = new ArrayList<>();
            int present = 0;
            for (String name : List.of("basicmagnet", "advancedmagnet",
                    "basic_demagnetization_coil", "advanced_demagnetization_coil")) {
                String body = read("/data/simplemagnets/recipe/" + name + ".json");
                if (body == null) {
                    continue;
                }
                present++;
                JsonObject root = JsonParser.parseString(body).getAsJsonObject();

                boolean guarded = false;
                if (root.has("neoforge:conditions")) {
                    for (JsonElement raw : root.getAsJsonArray("neoforge:conditions")) {
                        JsonObject cond = raw.getAsJsonObject();
                        guarded |= cond.has("type") && cond.has("modid")
                            && "neoforge:mod_loaded".equals(cond.get("type").getAsString())
                            && "simplemagnets".equals(cond.get("modid").getAsString());
                    }
                }
                if (!guarded) {
                    unguarded.add(name);
                }

                // Every id this file names, resolved. simplemagnets: ones cannot resolve without the
                // mod, so they are checked against the known set instead.
                List<String> ids = new ArrayList<>();
                for (var entry : root.getAsJsonObject("key").entrySet()) {
                    ids.add(entry.getValue().getAsString());
                }
                ids.add(root.getAsJsonObject("result").get("id").getAsString());
                for (String id : ids) {
                    if (id.startsWith("#")) {
                        continue;
                    }
                    if (id.startsWith("simplemagnets:")) {
                        if (!known.contains(id)) {
                            badIds.add(name + " -> " + id);
                        }
                    } else if (BuiltInRegistries.ITEM.getValue(Identifier.parse(id))
                            == net.minecraft.world.item.Items.AIR) {
                        badIds.add(name + " -> " + id);
                    }
                }
            }
            for (var holder : level.recipeAccess().recipeMap().values()) {
                if ("simplemagnets".equals(holder.id().identifier().getNamespace())) {
                    loaded.add(holder.id().identifier().toString());
                }
            }

            helper.assertTrue(unguarded.isEmpty(),
                "these override another mod's recipe id without a neoforge:mod_loaded condition "
                    + "naming simplemagnets, so without that mod they do not merely fail to apply - "
                    + "they FAIL TO PARSE, which is one ERROR line in an otherwise green run. An "
                    + "EMPTY condition array counts as unguarded here, because NeoForge reads it as "
                    + "all-conditions-met: " + unguarded);
            helper.assertTrue(badIds.isEmpty(),
                "these ids do not resolve, and the consequence is not what it looks like: this mod is "
                    + "ordered AFTER simplemagnets, so ours is the ONLY file read at that path. It "
                    + "fails to parse, the id is absent entirely, and the magnet becomes UNCRAFTABLE "
                    + "rather than falling back to the stock recipe: " + badIds);
            helper.assertTrue(loaded.isEmpty(),
                "recipes loaded for a mod that is not present: " + loaded);
            helper.assertTrue(present == 0 || present == 4,
                "found " + present + " of the 4 Simple Magnets overrides. Partial is the bad state: "
                    + "re-theming two recipes and leaving two stock is worse than doing neither, and "
                    + "removing the stopgap means removing all four.");
            helper.succeed();
        });
    }

    /** One ingredient's JSON form to a concrete item; a tag resolves to any one member. */
    private static Item resolve(JsonElement element, Map<String, Item> cache) {
        String value = element.isJsonObject() && element.getAsJsonObject().has("item")
            ? element.getAsJsonObject().get("item").getAsString()
            : element.getAsString();
        if (!value.startsWith("#")) {
            return BuiltInRegistries.ITEM.getValue(Identifier.parse(value));
        }
        return cache.computeIfAbsent(value, key -> {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(key.substring(1)));
            return BuiltInRegistries.ITEM.get(tag)
                .flatMap(set -> set.stream().findFirst())
                .map(Holder::value)
                .orElse(Items.AIR);
        });
    }

    /** One bundled JSON as text, or null. */
    private static String read(String path) {
        try (java.io.InputStream in = RecipeReachabilityTests.class.getResourceAsStream(path)) {
            return in == null ? null
                : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException failed) {
            return null;
        }
    }
}
