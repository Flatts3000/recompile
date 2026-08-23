package com.flatts.recompile.content;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A biome's fog, sky and ambient settings must live under {@code attributes}, not under
 * {@code effects}.
 *
 * <p><b>This is a guard against a defect that shipped in all four biomes and rendered nothing for
 * releases.</b> 26.1 split {@code BiomeSpecialEffects}: it now carries {@code water_color},
 * {@code foliage_color}, {@code dry_foliage_color}, {@code grass_color} and
 * {@code grass_color_modifier} and nothing else. Fog, sky, water fog, ambient particles and every
 * sound moved to a top-level {@code attributes} map - the environment-attribute system, keyed by
 * registered attribute id.
 *
 * <p>The failure mode is the nastiest kind. A record codec ignores map keys it does not know, so
 * {@code fog_color} sitting in {@code effects} parses fine, logs nothing, and does nothing. There is
 * no error to grep for and no missing texture to see - the world just quietly keeps the default. It
 * survived a full review: #286 changed this mod's two frontier regions to stop them looking identical
 * at range, and only the grass and foliage half of that change ever ran.
 *
 * <p>Two directions are asserted, and both are needed. That {@code effects} holds nothing the record
 * cannot read catches a value put in the dead half. That the moved keys appear under
 * {@code attributes} catches the opposite mistake - deleting a setting rather than relocating it -
 * which would also be silent, and would also just render the default.
 *
 * <p>Deliberately reads the shipped JSON rather than the loaded registry. The whole point is what is
 * written on disk: a loaded biome has already thrown the ignored keys away, so asking the registry
 * would reproduce exactly the blindness that let this ship.
 */
class BiomeEffectsPlacementTest {

    /** Everything {@code BiomeSpecialEffects} still reads in 26.1. Verified against the patched jar. */
    private static final Set<String> EFFECTS_FIELDS = Set.of(
        "water_color", "foliage_color", "dry_foliage_color", "grass_color", "grass_color_modifier");

    /**
     * Keys that used to live in {@code effects} and now name an environment attribute. Anything here
     * found under {@code effects} is being ignored by the game.
     */
    private static final Set<String> MOVED = Set.of(
        "fog_color", "sky_color", "water_fog_color", "particle", "ambient_sound", "mood_sound",
        "additions_sound", "music", "music_volume");

    private static Path biomeDir() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src/main/resources/data/recompile/worldgen/biome");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Test
    void biome_effects_hold_only_what_the_game_still_reads() throws IOException {
        Path dir = biomeDir();
        assertTrue(dir != null, "could not find the biome directory, so this test measured nothing");

        List<String> problems = new java.util.ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                checked++;
                JsonObject biome = read(file);
                JsonObject effects = biome.getAsJsonObject("effects");
                if (effects == null) {
                    continue;
                }
                for (String key : new TreeSet<>(effects.keySet())) {
                    if (key.startsWith("_")) {
                        continue;
                    }
                    if (MOVED.contains(key)) {
                        problems.add(file.getFileName() + ": '" + key + "' is in \"effects\", where "
                            + "26.1 ignores it silently. It belongs under \"attributes\".");
                    } else if (!EFFECTS_FIELDS.contains(key)) {
                        problems.add(file.getFileName() + ": '" + key + "' is not a field of "
                            + "BiomeSpecialEffects, so it is being dropped without an error.");
                    }
                }
            }
        }
        assertTrue(checked > 0, "no biome files were read, so this test measured nothing");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void every_biome_states_its_fog_and_sky_where_they_are_read() throws IOException {
        Path dir = biomeDir();
        assertTrue(dir != null, "could not find the biome directory, so this test measured nothing");

        List<String> problems = new java.util.ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject biome = read(file);
                JsonObject attributes = biome.getAsJsonObject("attributes");
                if (attributes == null) {
                    problems.add(file.getFileName() + ": no \"attributes\" at all, so its fog, sky and "
                        + "ambient sound are all whatever the dimension defaults to.");
                    continue;
                }
                for (String needed : List.of("visual/fog_color", "visual/sky_color")) {
                    if (!attributes.has(needed)) {
                        problems.add(file.getFileName() + ": \"attributes\" has no '" + needed + "'. "
                            + "Every biome in this mod sets one; a missing one is a setting that was "
                            + "deleted rather than moved, which renders as the default with no error.");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
