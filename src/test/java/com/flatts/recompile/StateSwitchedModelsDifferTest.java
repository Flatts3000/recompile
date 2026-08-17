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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * If a blockstate switches between two models, those two models must actually look different.
 *
 * <p><b>The bug this is written from.</b> The Trommel's drum has an {@code ACTIVE} blockstate, a
 * mirror in {@code TrommelBlockEntity.setActive} that writes it onto all four drum cells, and a
 * blockstate mapping every {@code active=true} variant to a {@code _running} model. All of that
 * machinery worked. The two model files were <b>byte-identical</b>, so the drum turned whether or not
 * the machine had power or material - an unpowered machine visibly running, which is the game telling
 * the player something false.
 *
 * <p>Nothing could catch it. GameTest and JUnit never draw a block face, so the whole suite was green;
 * the blockstate was complete and correct; both models were valid and both resolved. It took a
 * screenshot of the two states, and then a byte-compare to be sure the eye was not inventing the
 * match.
 *
 * <p>So the rule is structural: <b>two distinct model files reachable from one blockstate may not
 * normalize to the same content.</b> A pair that does is either a copy-paste that forgot to change
 * anything, or a state that should never have existed. Rotation is not an exception - a variant that
 * differs only by angle reuses the same model name with {@code "x"}/{@code "y"}, so it never reaches
 * here.
 */
class StateSwitchedModelsDifferTest {

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

    /** Every {@code "model"} value anywhere inside a blockstate file, in one flat set. */
    private static void collectModels(JsonElement element, Set<String> into) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()) {
                    into.add(entry.getValue().getAsString());
                } else {
                    collectModels(entry.getValue(), into);
                }
            }
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectModels(child, into));
        }
    }

    /** Content with comments and formatting removed, so only what the renderer reads is compared. */
    private static String normalize(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                // _comment / __comment are ignored by the game's codecs, so two models that differ
                // only in their prose are the same picture and must still fail.
                if (entry.getKey().startsWith("_")) {
                    continue;
                }
                out.add(entry.getKey(), JsonParser.parseString(normalize(entry.getValue())));
            }
            return out.toString();
        }
        return element.toString();
    }

    private static Path modelPath(String id) {
        String path = id.startsWith("recompile:") ? id.substring("recompile:".length()) : null;
        if (path == null) {
            return null;   // vanilla and other namespaces are not ours to read
        }
        return RESOURCES.resolve("assets").resolve("recompile").resolve("models")
            .resolve(path + ".json");
    }

    @Test
    void two_models_a_blockstate_switches_between_are_never_the_same_picture() throws IOException {
        Path states = RESOURCES.resolve("assets").resolve("recompile").resolve("blockstates");
        assertTrue(Files.isDirectory(states),
            "blockstates not found at " + states.toAbsolutePath()
                + " - a walk over nothing would pass by checking nothing");

        List<String> clashes = new ArrayList<>();
        int checkedStates = 0;
        int comparedModels = 0;

        try (Stream<Path> files = Files.walk(states)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                JsonElement root;
                try {
                    root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                } catch (RuntimeException parseFailure) {
                    continue;   // ResourceJsonSyntaxTest owns syntax; this one owns meaning
                }
                Set<String> models = new LinkedHashSet<>();
                collectModels(root, models);
                if (models.size() < 2) {
                    continue;   // one model cannot disagree with itself
                }
                checkedStates++;

                Map<String, String> byContent = new HashMap<>();
                for (String id : models) {
                    Path model = modelPath(id);
                    if (model == null || !Files.isRegularFile(model)) {
                        continue;
                    }
                    comparedModels++;
                    String content = normalize(
                        JsonParser.parseString(Files.readString(model, StandardCharsets.UTF_8)));
                    String twin = byContent.putIfAbsent(content, id);
                    if (twin != null) {
                        clashes.add(file.getFileName() + ": " + twin + " and " + id
                            + " are the same picture, so switching between them shows nothing");
                    }
                }
            }
        }

        assertTrue(checkedStates > 0 && comparedModels > 0,
            "no multi-model blockstate was examined (" + checkedStates + " states, "
                + comparedModels + " models) - the walk found nothing and would pass vacuously");
        assertTrue(clashes.isEmpty(),
            "blockstates switch between identical models:\n  " + String.join("\n  ", clashes));
    }
}
