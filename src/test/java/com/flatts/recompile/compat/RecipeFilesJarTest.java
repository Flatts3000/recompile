package com.flatts.recompile.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The bundled recipes are readable from inside a JAR, not just from an exploded folder.
 *
 * <p><b>This is the one failure no other test layer in this repo can see.</b> GameTests and the rest
 * of the JUnit suite both run against {@code build/resources/main} - a real directory - so both take
 * the working branch and report green while the shipped jar reads nothing at all.
 *
 * <p>What shipped: {@code RecipeFiles} resolved its anchor with {@code Path.of(url.toURI())}, which
 * is right for a {@code file:} URL and throws {@code FileSystemNotFoundException} for the
 * {@code jar:...!/data/...} URL every packaged install produces. The throw was caught into an empty
 * list, so {@code TeardownData} knew about no teardowns whatsoever: <b>Jade told players "No salvage
 * value" for every item they held</b> and JEI's Teardown category was empty, while the teardown
 * itself worked perfectly because the server runs the real recipe manager. A mechanic that works and
 * is denied by every viewer is worse than one that is broken, because nothing looks wrong enough to
 * report. It took a playtest holding a Dirty Mattress - the item the whole blueprint loop runs on.
 *
 * <p>So this builds an actual jar and reads out of it. Driving it red is a one-line revert of
 * {@code scan} to the old {@code Path.of} call.
 */
class RecipeFilesJarTest {

    private static final String DIR = "data/recompile/recipe/";

    /** A jar laid out the way the mod's own is, so the anchor resolves the same way. */
    private static Path buildJar(Path dir) throws Exception {
        Path jar = dir.resolve("fake-mod.jar");
        try (OutputStream out = Files.newOutputStream(jar);
                JarOutputStream jos = new JarOutputStream(out)) {
            write(jos, DIR + "mattress.json",
                "{\"type\":\"recompile:teardown\",\"input\":\"recompile:mattress\"}");
            write(jos, DIR + "washing_machine.json",
                "{\"type\":\"recompile:teardown\",\"input\":\"recompile:washing_machine\"}");
            write(jos, DIR + "not_a_teardown.json",
                "{\"type\":\"minecraft:crafting_shapeless\"}");
        }
        return jar;
    }

    private static void write(JarOutputStream jos, String name, String body) throws Exception {
        jos.putNextEntry(new JarEntry(name));
        jos.write(body.getBytes(StandardCharsets.UTF_8));
        jos.closeEntry();
    }

    @Test
    @DisplayName("recipes inside a jar are found, which is every install that is not a dev run")
    void readsFromInsideAJar(@TempDir Path dir) throws Exception {
        Path jar = buildJar(dir);
        URL anchor = new URL("jar:" + jar.toUri() + "!/" + DIR + "mattress.json");

        // Sanity first: if the anchor itself does not resolve, everything below passes vacuously
        // by finding the nothing it expected - the exact shape of the bug being guarded against.
        //
        // setUseCaches(false) because JarURLConnection shares an open-jar cache by default, which
        // holds the file open past this block and leaves @TempDir unable to delete it on Windows.
        var connection = anchor.openConnection();
        connection.setUseCaches(false);
        try (var in = connection.getInputStream()) {
            assertTrue(in.readAllBytes().length > 0, "the test jar's anchor did not open");
        }

        List<JsonObject> found = RecipeFiles.scan(anchor);
        assertEquals(3, found.size(),
            "every recipe beside the anchor should be read out of the jar, got " + found.size()
                + ". An empty list here is what shipped: Path.of on a jar: URI throws "
                + "FileSystemNotFoundException, it was caught, and every viewer silently believed "
                + "the mod had no teardowns.");

        long teardowns = found.stream()
            .filter(r -> r.has("type") && "recompile:teardown".equals(r.get("type").getAsString()))
            .count();
        assertEquals(2, teardowns, "the type filter must still work on jar-read recipes");
    }

    @Test
    @DisplayName("a folder still works, so the fix did not trade one layout for the other")
    void readsFromAFolder(@TempDir Path dir) throws Exception {
        Path recipes = dir.resolve(DIR);
        Files.createDirectories(recipes);
        Files.writeString(recipes.resolve("mattress.json"),
            "{\"type\":\"recompile:teardown\",\"input\":\"recompile:mattress\"}");
        Files.writeString(recipes.resolve("other.json"), "{\"type\":\"recompile:teardown\"}");

        List<JsonObject> found = RecipeFiles.scan(recipes.resolve("mattress.json").toUri().toURL());
        assertEquals(2, found.size(), "the exploded-folder path is what dev runs use; got " + found);
    }
}
