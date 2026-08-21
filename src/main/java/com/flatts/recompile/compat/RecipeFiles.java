package com.flatts.recompile.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.flatts.recompile.Recompile;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The mod's bundled recipe JSON, read off the classpath.
 *
 * <p><b>Why viewers read files instead of the recipe manager.</b> Recipes are not client-synced in
 * 26.1; {@code RCSyncedRecipes} catches them from {@code RecipesReceivedEvent}, but JEI builds its
 * categories on its own schedule and there is no ordering guarantee between the two. A category built
 * from an empty snapshot is not an error - it is simply a recipe a player cannot find, with nothing
 * anywhere saying why. Files are always there.
 *
 * <p><b>What this costs, stated plainly.</b> These are the recipes this MOD bundles, not the recipes
 * the server is running. A datapack that adds or overrides one is invisible here, so a pack extending
 * the blueprint system would have its recipes work in-world and not appear in JEI. That is a real
 * regression against reading the recipe manager, accepted because the alternative was worse: the
 * manager is reliably EMPTY at the moment JEI asks, so the choice was between "pack recipes missing"
 * and "all recipes missing". Revisit if a viewer-facing reload hook ever exists.
 *
 * <p><b>Discovered rather than listed.</b> {@code TeardownData} named its two recipe paths in a
 * constant, and when a third teardown shipped it was invisible to JEI - the Broken Hydroponics Bay
 * could be torn down in-world and no viewer would admit it existed. A hardcoded list is a second
 * inventory of the same facts, and the copy nobody remembers to update is always the one that is read.
 */
public final class RecipeFiles {

    /** Any recipe file, used to find the folder the rest live in. */
    private static final String ANCHOR = "/data/" + Recompile.MOD_ID + "/recipe/mattress.json";

    private static List<JsonObject> cached;

    private RecipeFiles() {
    }

    /** Every bundled recipe of the given type that is actually LOADED, parsed once and cached. */
    public static List<JsonObject> ofType(String type) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonObject recipe : all()) {
            if (recipe.has("type") && type.equals(recipe.get("type").getAsString())
                && conditionsHold(recipe)) {
                out.add(recipe);
            }
        }
        return out;
    }

    /**
     * Whether a bundled recipe's {@code neoforge:conditions} are satisfied in this install.
     *
     * <p><b>Reading files instead of the recipe manager means reading files the game did not load.</b>
     * A conditional recipe is still a file on the classpath when its condition fails, so without this
     * every viewer would advertise recipes that do not exist - and the ones that are conditional here
     * are conditional precisely because they need another mod, so the player has no way to make them
     * and no way to find out why. Caught by
     * {@code jei_sees_every_separating_recipe_with_its_real_count} when the AE2 sourcing recipes
     * landed (#276): the game ran 7 separating recipes and JEI read 10.
     *
     * <p><b>{@code neoforge:mod_loaded} and {@code neoforge:not} are evaluated; anything else is
     * treated as SATISFIED.</b> That is the honest bias for a viewer: showing one recipe too many is a
     * worse afternoon than hiding a real one. The previous version of this handled only
     * {@code mod_loaded} and said that it "needs to grow rather than be trusted" if a type that can be
     * false at runtime ever appeared - and then #277 shipped one, {@code neoforge:not}, wrapping the
     * guard on the sky stone strip modifier. Unrecognised, it read as satisfied, so the viewer thought
     * the strip was active even with AE2 installed and hid a drop the game was really producing.
     * Caught by {@code pull_rates_match_what_the_mod_claims} on the with-AE2 run.
     *
     * <p>{@code neoforge:and} and {@code neoforge:or} are deliberately NOT handled, because this mod
     * ships neither and an untested branch is worse than an absent one. Adding one means adding it
     * here too; the default is silent, which is exactly why it is called out.
     *
     * <p><b>Public because {@code SortingData} needs the same answer about loot tables and loot
     * modifiers.</b> It had a byte-for-byte copy of this method, which review of #277 called out: two
     * evaluators of a silently-failing condition drift in both directions at once, and neither
     * direction announces itself - one viewer over-reports drops that cannot happen while the other
     * hides real ones.
     */
    public static boolean conditionsHold(JsonObject recipe) {
        if (!recipe.has("neoforge:conditions") || !recipe.get("neoforge:conditions").isJsonArray()) {
            // Not an array is malformed rather than false. Reading it as one threw ClassCastException
            // straight out of ofType and into JEI category construction, undoing this class's own
            // rule that a viewer failure must never become a crash on world join (#277).
            return true;
        }
        for (var raw : recipe.getAsJsonArray("neoforge:conditions")) {
            if (raw.isJsonObject() && !holds(raw.getAsJsonObject())) {
                return false;
            }
        }
        return true;
    }

    /** One condition. Recursive, so {@code neoforge:not} can wrap another. */
    private static boolean holds(JsonObject condition) {
        if (!condition.has("type")) {
            return true;
        }
        String type = condition.get("type").getAsString();
        if ("neoforge:mod_loaded".equals(type)) {
            return !condition.has("modid")
                || net.neoforged.fml.ModList.get().isLoaded(condition.get("modid").getAsString());
        }
        if ("neoforge:not".equals(type)) {
            // A malformed not() is satisfied rather than inverted: guessing at what it meant to negate
            // is how a viewer ends up confidently wrong in the one direction it is meant to avoid.
            return !condition.has("value") || !condition.get("value").isJsonObject()
                || !holds(condition.getAsJsonObject("value"));
        }
        return true;
    }

    /**
     * Every bundled recipe file.
     *
     * <p>Anchored on a known file rather than the directory, because asking a classloader for a
     * directory does not reliably return a URL - the same reason {@code GuidebookTests} anchors on
     * {@code book.json}. A read failure yields an empty list: a viewer missing a category is a bad
     * afternoon, and a crash on world join is a worse one.
     */
    public static synchronized List<JsonObject> all() {
        if (cached != null) {
            return cached;
        }
        cached = folder(ANCHOR);
        return cached;
    }

    /**
     * Every bundled JSON file sitting beside {@code anchorResource}, parsed.
     *
     * <p>The generic half of {@link #all()}, exposed because {@code loot_modifiers/} needs reading the
     * same way and for the same reason: a viewer that models what the player will see has to know
     * which modifiers are active. Same anchoring trick, and more importantly the same jar-safe
     * {@link #scan} - a second folder reader would be a second chance to reintroduce the
     * {@code FileSystemNotFoundException} bug that made every viewer deny the teardown system in
     * packaged installs.
     */
    public static List<JsonObject> folder(String anchorResource) {
        URL anchor = RecipeFiles.class.getResource(anchorResource);
        return anchor == null ? List.of() : List.copyOf(scan(anchor));
    }

    /**
     * Read every recipe beside {@code anchor}, whether it lives in a folder or inside a jar.
     *
     * <p><b>The jar half is why this method exists, and its absence shipped.</b> The original walked
     * {@code Path.of(anchor.toURI())} and caught everything into an empty list. That is fine in a dev
     * run, where mod resources are an exploded directory and the URL is {@code file:} - and it is
     * broken in every packaged install, where the URL is {@code jar:...!/data/...} and
     * {@code Path.of} throws {@code FileSystemNotFoundException} because no filesystem is open for
     * the jar yet.
     *
     * <p>So {@code TeardownData} came back empty for real players: <b>Jade reported "No salvage
     * value" for every item</b>, the Broken Hydroponics Bay included, and JEI's Teardown category was
     * empty. The teardown itself worked the whole time - the server runs the real recipe manager - so
     * the only symptom was the viewers flatly denying a mechanic that was working. Reported from a
     * playtest holding a Dirty Mattress, the item the entire blueprint loop runs on.
     *
     * <p><b>No test layer could have caught it.</b> GameTests and the JUnit suite both run against
     * exploded resources, so both take the working path. {@code RecipeFilesJarTest} builds an actual
     * jar and reads from it, which is the only way this failure is visible from inside the repo.
     */
    static List<JsonObject> scan(URL anchor) {
        List<JsonObject> recipes = new ArrayList<>();
        try {
            URI uri = anchor.toURI();
            if ("jar".equals(uri.getScheme())) {
                // Reuse an already-open filesystem where there is one - NeoForge may hold the mod jar
                // open - and only close the one we opened ourselves. Closing someone else's would
                // break every later read from that jar.
                FileSystem opened = null;
                FileSystem fs;
                try {
                    // The branch that runs in the real game: NeoForge already holds the mod jar open,
                    // so this succeeds and `opened` stays null, and we must NOT close what we did not
                    // open. Covered by reusesAnAlreadyOpenFilesystem, because the untested half of a
                    // two-branch fix is the half that ships broken.
                    fs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException notOpenYet) {
                    fs = opened = FileSystems.newFileSystem(uri, Map.of());
                }
                try {
                    // The entry comes from the URL, not from ANCHOR. Using the constant here made the
                    // jar branch ignore its own argument while the folder branch honoured it, so the
                    // method quietly meant two different things depending on packaging - and the
                    // jar test only passed because its fixture happened to use the same layout.
                    String entry = uri.toString().substring(uri.toString().indexOf('!') + 1);
                    collect(fs.getPath(entry).getParent(), recipes);
                } finally {
                    if (opened != null) {
                        opened.close();
                    }
                }
            } else {
                // file:, and NeoForge's own union: scheme - both resolve through an installed
                // provider, so Path.of is the right call and the jar branch above is the exception.
                collect(Path.of(uri).getParent(), recipes);
            }
        } catch (IOException | URISyntaxException | RuntimeException ignored) {
            // Left empty; callers already handle having nothing.
        }
        return recipes;
    }

    private static void collect(Path dir, List<JsonObject> recipes) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                if (!path.toString().endsWith(".json")) {
                    continue;
                }
                try {
                    recipes.add(JsonParser
                        .parseString(Files.readString(path, StandardCharsets.UTF_8))
                        .getAsJsonObject());
                } catch (RuntimeException | IOException ignored) {
                    // One malformed file must not take the whole viewer down with it.
                }
            }
        }
    }
}
