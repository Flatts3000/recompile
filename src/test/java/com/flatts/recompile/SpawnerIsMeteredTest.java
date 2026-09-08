package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Making a spawner must consume a Broken Spawner.
 *
 * <p><b>The Blueprint is permanent, so the recipe is the only meter there is.</b> A sheet bought once
 * from the Buy Terminal for 1500 scrip makes spawners forever, and #294's own recipe comment said so
 * and called the missing rate limit out by name - then the sentence went stale and nobody noticed for
 * two releases. It read "four Broken Spawners buy spawners forever and there is no second lever",
 * which described a world where teardown taught the sheet; #390 stopped teardown teaching anything
 * and the sheet moved to the market, so the find stopped touching spawners at all and the faucet ran
 * with nothing metering it.
 *
 * <p>That is the whole reason this is a test rather than another comment. <b>The comment was right
 * about the risk, was the only thing guarding it, and still rotted</b> - into four copies, three of
 * which were still repeating it when this was written. A Broken Spawner is 1 in 2000 depths pulls,
 * about the Bucket's rarity, so consuming one per craft makes spawners rate-limited rather than
 * unlimited, and rate-limited is what a progression gate is.
 *
 * <p><b>It reads the ingredients, not the file.</b> The first version scanned the whole recipe object
 * for the id as a substring, which is prose-shaped in exactly the way this test exists to replace: a
 * nested {@code _comment} mentioning the item would have satisfied it, and this repo ships nested
 * comments inside recipe pools. It now pulls the ids out of {@code key} (only for letters that
 * actually appear in the {@code pattern}) or {@code ingredients}, and compares them exactly.
 *
 * <p>Note what this does NOT say: it does not require the find to be the only cost, or fix the rest
 * of the grid, or care which cell it sits in. Only that the meter is still there.
 */
class SpawnerIsMeteredTest {

    private static final String SPAWNER = "minecraft:spawner";
    private static final String BROKEN_SPAWNER = "recompile:broken_spawner";

    private static Path resourceRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src").resolve("main").resolve("resources");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return Path.of("src", "main", "resources");
    }

    private static String itemOf(JsonElement ingredient) {
        if (ingredient == null || ingredient.isJsonNull()) {
            return null;
        }
        if (ingredient.isJsonPrimitive()) {
            return ingredient.getAsString();
        }
        if (ingredient.isJsonObject()) {
            JsonObject object = ingredient.getAsJsonObject();
            for (String key : List.of("item", "id")) {
                if (object.has(key) && object.get(key).isJsonPrimitive()) {
                    return object.get(key).getAsString();
                }
            }
        }
        return null;
    }

    /** Every item id this recipe actually consumes, from wherever its schema keeps them. */
    private static Set<String> consumed(JsonObject recipe) {
        Set<String> ids = new LinkedHashSet<>();
        if (recipe.has("key") && recipe.get("key").isJsonObject()) {
            // Only letters the pattern uses: a stray key entry is not an ingredient.
            StringBuilder pattern = new StringBuilder();
            if (recipe.has("pattern") && recipe.get("pattern").isJsonArray()) {
                for (JsonElement row : recipe.getAsJsonArray("pattern")) {
                    pattern.append(row.getAsString());
                }
            }
            for (Map.Entry<String, JsonElement> entry : recipe.getAsJsonObject("key").entrySet()) {
                if (pattern.indexOf(entry.getKey()) < 0) {
                    continue;
                }
                collect(entry.getValue(), ids);
            }
        }
        if (recipe.has("ingredients") && recipe.get("ingredients").isJsonArray()) {
            for (JsonElement ingredient : recipe.getAsJsonArray("ingredients")) {
                collect(ingredient, ids);
            }
        }
        collect(recipe.get("ingredient"), ids);
        return ids;
    }

    private static void collect(JsonElement ingredient, Set<String> into) {
        if (ingredient != null && ingredient.isJsonArray()) {
            for (JsonElement one : ingredient.getAsJsonArray()) {
                collect(one, into);
            }
            return;
        }
        String id = itemOf(ingredient);
        if (id != null) {
            into.add(id);
        }
    }

    private static String result(JsonObject recipe) {
        if (!recipe.has("result") || !recipe.get("result").isJsonObject()) {
            return null;
        }
        return itemOf(recipe.get("result"));
    }

    @Test
    void every_recipe_that_makes_a_spawner_consumes_a_broken_one() throws IOException {
        Path data = resourceRoot().resolve("data");
        assertTrue(Files.isDirectory(data), "no data directory at " + data);

        // EVERY namespace, not just this mod's. Recipes ship under data/minecraft and data/enderio
        // as a matter of routine - overriding another mod's id is how a recipe gets disabled - so a
        // spawner recipe added under one of those would have escaped a scan of data/recompile/recipe
        // alone, silently, while the guard still reported green. The set of foreign namespaces MOVES:
        // data/simplemagnets was in this sentence until its four overrides went back to the pack
        // (#420), which is why the scan below lists the directory rather than naming namespaces.
        List<Path> files = new ArrayList<>();
        try (Stream<Path> namespaces = Files.list(data)) {
            for (Path namespace : namespaces.filter(Files::isDirectory).sorted().toList()) {
                Path recipes = namespace.resolve("recipe");
                if (!Files.isDirectory(recipes)) {
                    continue;
                }
                try (Stream<Path> tree = Files.walk(recipes)) {
                    files.addAll(tree.filter(p -> p.toString().endsWith(".json")).sorted().toList());
                }
            }
        }
        assertTrue(files.size() > 50,
            "found only " + files.size() + " recipe files - the walk found nothing");

        List<String> makers = new ArrayList<>();
        List<String> unmetered = new ArrayList<>();
        for (Path file : files) {
            JsonElement parsed =
                JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                continue;
            }
            JsonObject recipe = parsed.getAsJsonObject();
            if (!SPAWNER.equals(result(recipe))) {
                continue;
            }
            String id = data.relativize(file).toString().replace('\\', '/');
            makers.add(id);
            if (!consumed(recipe).contains(BROKEN_SPAWNER)) {
                unmetered.add(id);
            }
        }

        assertTrue(!makers.isEmpty(),
            "no recipe makes a " + SPAWNER + " - either the scan is broken or the rule now guards "
                + "nothing, and both want looking at rather than a green tick");
        assertTrue(unmetered.isEmpty(),
            "these make a spawner without consuming a " + BROKEN_SPAWNER + ": " + unmetered
                + "\nThe Blueprint is permanent, so the recipe is the only thing rate-limiting "
                + "spawners. Without the find in the grid, one purchase makes them forever.");
    }
}
