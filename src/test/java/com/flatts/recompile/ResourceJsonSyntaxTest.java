package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every JSON resource this mod ships must be STRICT JSON.
 *
 * <p>This exists because a real bug shipped through the whole gate. A guidebook string was written
 * with literal newlines inside it rather than {@code \n} escapes, which is invalid JSON - and
 * nothing noticed. Minecraft parses its resources with Gson in <b>lenient</b> mode, so the file
 * loaded, the text rendered, all 398 GameTests passed, CI went green, and it merged.
 *
 * <p>That is the worst shape a defect can have: correct-looking at runtime and wrong on disk. It
 * survives only until something with a stricter parser reads it - a datapack tool, a translation
 * platform, a pack build step, {@code json.load} in any script - and then it fails somewhere far
 * from the change that caused it.
 *
 * <p>So this parses in strict mode deliberately, rather than reusing the game's reader, which would
 * reproduce the leniency that hid the problem in the first place.
 */
class ResourceJsonSyntaxTest {

    /**
     * Walks up from the working directory to find the resource root.
     *
     * <p>Not a plain relative path: moddev runs the JUnit layer with the working directory set to
     * {@code build/minecraft-junit}, not the project root. A relative path there resolves to nothing,
     * and a walk over nothing finds no problems - which is why the assertion below checks the root
     * was found at all rather than trusting it.
     */
    private static Path resourceRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src").resolve("main").resolve("resources");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return Path.of("src", "main", "resources");
    }

    private static final Path RESOURCES = resourceRoot();

    @Test
    void every_shipped_json_resource_is_strict_json() throws IOException {
        assertTrue(Files.isDirectory(RESOURCES),
            "resource root not found at " + RESOURCES.toAbsolutePath()
                + " - if the working directory changed this test would pass by finding nothing");

        List<String> broken = new ArrayList<>();
        int checked = 0;

        try (Stream<Path> files = Files.walk(RESOURCES)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                checked++;
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonReader strict = new JsonReader(reader);
                    strict.setStrictness(com.google.gson.Strictness.STRICT);
                    JsonParser.parseReader(strict);
                } catch (Exception e) {
                    broken.add(RESOURCES.relativize(file) + ": " + e.getMessage());
                }
            }
        }

        // A count assertion, because "walked nothing and found no problems" is the way this test
        // would rot into always passing.
        assertTrue(checked > 100,
            "expected to check well over 100 JSON resources, only saw " + checked);
        assertTrue(broken.isEmpty(), "these shipped resources are not valid JSON:\n  "
            + String.join("\n  ", broken));
    }
}
