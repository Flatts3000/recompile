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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A terminal is repaired from a Broken Terminal, and there is no other way to get one.
 *
 * <p><b>This rule has been inverted once, by the owner, and the history is the useful part.</b> The
 * find taught both market terminals until #390 took its teaching away and nothing replaced the role,
 * leaving a Broken Terminal as a generic bag of scrap shaped like a terminal. #406 gave it a repair
 * recipe but kept the plain one beside it, on the argument that the Buy Terminal is the only source
 * of every Blueprint in the game and the Sell Terminal the only source of scrip, so requiring the
 * find would put the whole progression spine behind a 1-in-16 roll. <b>The owner ruled the other way
 * on 2026-09-07</b> - "remove the recipes that don't take broken terminals" - and this test now
 * enforces that, so the earlier version of it, which failed on a terminal that had ONLY the repair,
 * would fail on today's repo.
 *
 * <p>What the ruling buys: a terminal reads as a thing you found and fixed rather than a thing you
 * fabricated, which is this mod's premise applied to its own shop counter. What makes it safe is the
 * find rate rather than the recipe - 1 in 17.8 Bulky Waste (1 of 16 in {@code bulky_spine}, which is 9
 * of 10 of the table), and Bulky Waste is 1 in 20 of a mound's core cells. <b>If that ever stops being
 * true these are the first recipes to revisit</b>, because
 * everything the market gates is downstream of them.
 *
 * <p>The set of terminals is derived from the recipes rather than listed, so a fourth is covered the
 * day it ships - a hand-list is exactly how the find ended up with no use but scrap.
 */
class TerminalRoutesTest {

    private static final String BROKEN = "recompile:broken_terminal";

    /**
     * Ids that end in {@code _terminal} and are not one, with the reason. Empty today, and it exists
     * because the rule below is keyed on a NAME: the moment something borrows the noun without being
     * part of the market spine - a decorative terminal, a dead-screen prop, a machine that happens to
     * be called one - the build fails with a message about Blueprints and scrip that does not apply
     * to it. The repo's convention is a justified entry rather than a loosened check, the same shape
     * as {@code RegistryCompletenessTests}' own two lists.
     */
    private static final Set<String> NOT_A_MARKET_TERMINAL = Set.of();

    private static Path resourceRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src").resolve("main").resolve("resources");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return Path.of("src", "main", "resources");
    }

    private static String itemOf(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (String key : List.of("id", "item")) {
                if (object.has(key) && object.get(key).isJsonPrimitive()) {
                    return object.get(key).getAsString();
                }
            }
        }
        return null;
    }

    /**
     * Every item id this recipe consumes.
     *
     * <p><b>Read out of the ingredients, never scanned for as a substring.</b> The sibling
     * {@code SpawnerIsMeteredTest} shipped with the substring version and review caught it: this
     * repo's recipes carry long {@code _comment} blocks that discuss their own ingredients by id, and
     * nested comments inside pools too, so a mention in prose satisfied the check. A test written
     * because a comment was the only guard, satisfied by a comment.
     */
    private static Set<String> consumed(JsonObject recipe) {
        Set<String> ids = new LinkedHashSet<>();
        if (recipe.has("key") && recipe.get("key").isJsonObject()) {
            StringBuilder pattern = new StringBuilder();
            if (recipe.has("pattern") && recipe.get("pattern").isJsonArray()) {
                for (JsonElement row : recipe.getAsJsonArray("pattern")) {
                    pattern.append(row.getAsString());
                }
            }
            for (Map.Entry<String, JsonElement> entry : recipe.getAsJsonObject("key").entrySet()) {
                if (pattern.indexOf(entry.getKey()) >= 0) {
                    collect(entry.getValue(), ids);
                }
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

    /**
     * What this recipe puts in the player's hands, whichever way its schema spells that.
     *
     * <p><b>A market offer counts, and missing it was the hole review found.</b> A
     * {@code recompile:market_offer} has no {@code result} at all - it names its goods in a
     * top-level {@code item}, as a bare string - so an offer selling a terminal over the counter
     * would have been a second route with the find skipped entirely, and this test would still have
     * gone green. That is not hypothetical: the shelf already sells three items outright. The
     * primitive-string spelling of {@code result} is handled for the same reason.
     */
    private static String result(JsonObject recipe) {
        if (recipe.has("result")) {
            return itemOf(recipe.get("result"));
        }
        String type = recipe.has("type") ? recipe.get("type").getAsString() : "";
        if (type.equals("recompile:market_offer")) {
            return itemOf(recipe.get("item"));
        }
        return null;
    }

    @Test
    void a_terminal_can_only_be_had_by_repairing_a_broken_one() throws IOException {
        Path data = resourceRoot().resolve("data");
        assertTrue(Files.isDirectory(data), "no data directory at " + data);

        // Every namespace, because overriding another mod's recipe id is routine here.
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

        Map<String, List<String>> repaired = new TreeMap<>();
        Map<String, List<String>> fabricated = new TreeMap<>();
        for (Path file : files) {
            JsonElement parsed =
                JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                continue;
            }
            JsonObject recipe = parsed.getAsJsonObject();
            String made = result(recipe);
            if (made == null || !made.endsWith("_terminal") || made.equals(BROKEN)
                || NOT_A_MARKET_TERMINAL.contains(made)) {
                continue;
            }
            String id = data.relativize(file).toString().replace('\\', '/');
            (consumed(recipe).contains(BROKEN) ? repaired : fabricated)
                .computeIfAbsent(made, key -> new ArrayList<>()).add(id);
        }

        // The UNION - adding two map sizes double-counts anything in both, which is how a check
        // meant to catch a shrinking scan could pass on two terminals instead of three.
        Set<String> terminals = new TreeSet<>(repaired.keySet());
        terminals.addAll(fabricated.keySet());
        assertTrue(terminals.size() >= 3,
            "found only " + terminals.size() + " terminals " + terminals + " - the scan found nothing");

        List<String> problems = new ArrayList<>();
        for (String terminal : terminals) {
            if (!repaired.containsKey(terminal)) {
                problems.add(terminal + " cannot be repaired from a Broken Terminal, so the find has "
                    + "no use connected to what it is - the state #390 left it in.");
            }
        }
        for (Map.Entry<String, List<String>> entry : fabricated.entrySet()) {
            problems.add(entry.getKey() + " can be had without a Broken Terminal by " + entry.getValue()
                + ". The owner removed the plain recipes on 2026-09-07: a terminal is a thing you "
                + "found and fixed, not one you fabricated - and not one you buy over the counter "
                + "of the terminal you would need it to build.");
        }
        assertTrue(problems.isEmpty(), String.join("\n  ", problems));
    }
}
