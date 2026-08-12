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

    /** Every bundled recipe of the given type, parsed once and cached. */
    public static List<JsonObject> ofType(String type) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonObject recipe : all()) {
            if (recipe.has("type") && type.equals(recipe.get("type").getAsString())) {
                out.add(recipe);
            }
        }
        return out;
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
        URL anchor = RecipeFiles.class.getResource(ANCHOR);
        cached = anchor == null ? List.of() : List.copyOf(scan(anchor));
        return cached;
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
                    fs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException notOpenYet) {
                    fs = opened = FileSystems.newFileSystem(uri, Map.of());
                }
                try {
                    collect(fs.getPath(ANCHOR).getParent(), recipes);
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
