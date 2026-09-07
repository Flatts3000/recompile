package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every terminal must be buildable from parts AND repairable from a Broken Terminal, and neither
 * route may be the only one.
 *
 * <p><b>Both halves are load-bearing and they fail in opposite directions.</b>
 *
 * <p><b>No repair route</b> is what this was written for (owner, 2026-09-07: a Broken Terminal
 * should be used for crafting terminals or removed). The find taught both market terminals until
 * #390 took its teaching away, and nothing replaced the role - so a Broken Terminal became a generic
 * bag of scrap that happened to be shaped like a terminal.
 *
 * <p><b>No plain route</b> is the deadlock #390 removed, and making the find a required ingredient
 * would bring it back in a new shape. The Buy Terminal is the only source of knowledge in the game
 * and the Sell Terminal is the only source of scrip, so gating either on a 1-in-16 Bulky Waste roll
 * puts the whole progression spine behind a die. The entry to the economy cannot sit behind a die
 * roll any more than it can sit behind the economy. That is why the repair is a SECOND recipe rather
 * than an edit to the first, and why this test fails on a terminal that has only the repair.
 *
 * <p>The set of terminals is derived from the recipes rather than listed, so a fourth one is covered
 * the day it ships - which is the point, because a hand-list is exactly how the mod ended up with a
 * find whose only remaining use was scrap.
 */
class TerminalRoutesTest {

    private static final String BROKEN = "recompile:broken_terminal";

    private static Path resourceRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src").resolve("main").resolve("resources");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return Path.of("src", "main", "resources");
    }

    /** The result id, whichever of the two spellings a recipe type uses for it. */
    private static String result(JsonObject recipe) {
        if (!recipe.has("result")) {
            return null;
        }
        JsonElement result = recipe.get("result");
        if (!result.isJsonObject()) {
            return result.isJsonPrimitive() ? result.getAsString() : null;
        }
        JsonObject object = result.getAsJsonObject();
        for (String key : List.of("id", "item")) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) {
                return object.get(key).getAsString();
            }
        }
        return null;
    }

    /** Does this recipe consume a Broken Terminal anywhere in its inputs? */
    private static boolean consumesBroken(JsonObject recipe) {
        JsonObject withoutResult = recipe.deepCopy();
        withoutResult.remove("result");
        // The comment blocks in this repo's recipes discuss the Broken Terminal at length, and a
        // comment is not an ingredient. Drop them before looking, or every plain terminal recipe
        // reads as a repair.
        withoutResult.remove("_comment");
        return withoutResult.toString().contains(BROKEN);
    }

    @Test
    void every_terminal_can_be_built_from_parts_and_repaired_from_a_find() throws IOException {
        Path recipes = resourceRoot().resolve("data").resolve("recompile").resolve("recipe");
        assertTrue(Files.isDirectory(recipes), "no recipe directory at " + recipes);

        Map<String, List<String>> plain = new TreeMap<>();
        Map<String, List<String>> repair = new TreeMap<>();
        try (Stream<Path> tree = Files.walk(recipes)) {
            for (Path file : tree.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject recipe = JsonParser
                    .parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                String result = result(recipe);
                if (result == null || !result.endsWith("_terminal") || result.equals(BROKEN)) {
                    continue;
                }
                String id = file.getFileName().toString().replace(".json", "");
                (consumesBroken(recipe) ? repair : plain)
                    .computeIfAbsent(result, key -> new ArrayList<>()).add(id);
            }
        }

        // A derivation that derives nothing passes for the wrong reason.
        assertTrue(plain.size() + repair.size() >= 3,
            "found only " + (plain.size() + repair.size()) + " terminals - the scan found nothing");

        List<String> problems = new ArrayList<>();
        for (String terminal : new TreeMap<>(plain).keySet()) {
            if (!repair.containsKey(terminal)) {
                problems.add(terminal + " can be built from parts but a Broken Terminal cannot be "
                    + "repaired into one. The find then has no use connected to what it is, which is "
                    + "the state #390 left it in and this rule exists to prevent.");
            }
        }
        for (String terminal : repair.keySet()) {
            if (!plain.containsKey(terminal)) {
                problems.add(terminal + " can ONLY be made from a Broken Terminal, so a 1-in-16 Bulky "
                    + "Waste roll gates it. For the Buy Terminal that is every Blueprint in the game "
                    + "and for the Sell Terminal it is all scrip: keep the plain recipe.");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n  ", problems));
    }
}
