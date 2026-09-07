package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * <p>That is the whole reason this test exists rather than another comment. <b>The comment was
 * right about the risk, was the only thing guarding it, and still rotted.</b> A Broken Spawner is
 * 1 in 2000 depths pulls - about the Bucket's rarity - so consuming one per craft makes spawners
 * rate-limited rather than unlimited, and rate-limited is what a progression gate is.
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

    @Test
    void every_recipe_that_makes_a_spawner_consumes_a_broken_one() throws IOException {
        Path recipes = resourceRoot().resolve("data").resolve("recompile").resolve("recipe");
        assertTrue(Files.isDirectory(recipes), "no recipe directory at " + recipes);

        List<String> makers = new ArrayList<>();
        List<String> unmetered = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(recipes)) {
            for (Path file : tree.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject recipe = JsonParser
                    .parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!recipe.has("result")) {
                    continue;
                }
                JsonObject result = recipe.get("result").isJsonObject()
                    ? recipe.getAsJsonObject("result") : null;
                if (result == null) {
                    continue;
                }
                String made = result.has("id") ? result.get("id").getAsString()
                    : result.has("item") ? result.get("item").getAsString() : null;
                if (!SPAWNER.equals(made)) {
                    continue;
                }
                String id = file.getFileName().toString().replace(".json", "");
                makers.add(id);
                // A comment is not an ingredient, and this file's comments discuss the find at
                // length - drop them before looking or the check passes on the prose alone.
                JsonObject inputs = recipe.deepCopy();
                inputs.remove("result");
                inputs.remove("_comment");
                if (!inputs.toString().contains(BROKEN_SPAWNER)) {
                    unmetered.add(id);
                }
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
