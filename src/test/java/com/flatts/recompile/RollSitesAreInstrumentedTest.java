package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every place that rolls a loot table is accounted for, and says whether it is measured.
 *
 * <p><b>Written because the first version of the analytics measured one path out of three and said
 * so in its own commit message.</b> The hook went into {@code SortableBlock.sort} with the claim that
 * it was "the ONE place a pull can happen" - which is true only of a pull by <i>hand</i>. The Sorting
 * Tarp and the Separator roll the same tables from their own code, and the first real playtest came
 * back with 136 blocks broken and <b>zero</b> pulls recorded, because the player mined the mound and
 * sifted it at a tarp. The instrumentation was not wrong about what it measured; it was wrong about
 * what it covered, and nothing could tell the difference from the inside.
 *
 * <p>So the set of roll sites is pinned here. A new one fails this test until somebody writes down
 * whether it feeds the log - which is the decision that got skipped, not the code.
 *
 * <p>A source-text check on purpose. The question is "does this call exist anywhere", and that is a
 * property of the tree rather than of anything loadable: a site that is never reached at runtime is
 * exactly the kind that goes unmeasured for months.
 */
class RollSitesAreInstrumentedTest {

    /**
     * Every file calling {@code getRandomItems}, and what it does about the log.
     *
     * <p>Keyed by file name with a reason attached, the same shape {@code RegistryCompletenessTests}
     * uses for its exemptions - an entry here is a claim somebody made deliberately.
     */
    private static final Map<String, String> KNOWN_SITES = Map.of(
        "SortableBlock.java", "hand sorting - records PULL",
        "SortingTarpBlock.java", "the tarp - records SIFT_TARP",
        "SeparatorBlockEntity.java", "automated sorting - records SIFT_SEPARATOR",
        "PigeonForageGoal.java", "pigeons pecking a pile - records FORAGE",
        // Not sorting and not a pull stream: the bay rolls its own seedling table, which is a
        // machine output rather than something a player's time converts into materials.
        "HydroponicsBayBlockEntity.java", "the seedling table - deliberately not a pull stream");

    private static Path mainSources() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src").resolve("main").resolve("java")
                .resolve("com").resolve("flatts").resolve("recompile");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return Path.of("src", "main", "java", "com", "flatts", "recompile");
    }

    @Test
    @DisplayName("no loot roll happens in a file nobody has decided about")
    void everyRollSiteIsAccountedFor() throws IOException {
        Path root = mainSources();
        assertTrue(Files.isDirectory(root), "sources not found at " + root.toAbsolutePath());

        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                if (file.toString().replace('\\', '/').contains("/gametest/")) {
                    continue;   // tests drive the real sites; they are not sources of play data
                }
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (text.contains("getRandomItems")) {
                    found.add(file.getFileName().toString());
                }
            }
        }

        assertTrue(found.size() >= 4,
            "expected several loot-roll sites, found " + found + " - the scan is broken, so this "
                + "would pass against a codebase that rolls tables everywhere");

        List<String> unaccounted = new ArrayList<>(found);
        unaccounted.removeAll(KNOWN_SITES.keySet());
        assertTrue(unaccounted.isEmpty(),
            "these files roll a loot table and nobody has said whether it reaches the analytics log: "
                + unaccounted + ".\nAdd the call (RCAnalytics.sifted / pull / foraged) or add the "
                + "file to KNOWN_SITES with a reason. An unmeasured roll site is not a gap you can "
                + "see - it looks exactly like a player who did not do that thing.");

        List<String> stale = new ArrayList<>(KNOWN_SITES.keySet());
        stale.removeAll(found);
        assertTrue(stale.isEmpty(),
            "these are listed as roll sites but no longer roll anything, so the list is describing "
                + "a codebase that no longer exists: " + stale);
    }

    @Test
    @DisplayName("every sorting roll site actually calls the recorder")
    void sortingSitesCallTheRecorder() throws IOException {
        Path root = mainSources();
        List<String> silent = new ArrayList<>();
        // The four that convert a player's time into materials. The seedling table is excluded
        // above and stays excluded here.
        for (String name : List.of("SortableBlock.java", "SortingTarpBlock.java",
                "SeparatorBlockEntity.java", "PigeonForageGoal.java")) {
            Path file;
            try (Stream<Path> files = Files.walk(root)) {
                file = files.filter(f -> f.getFileName().toString().equals(name))
                    .findFirst().orElse(null);
            }
            assertTrue(file != null, name + " has moved or been renamed; this list is stale");
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.contains("RCAnalytics.")) {
                silent.add(name);
            }
        }
        assertTrue(silent.isEmpty(),
            "these turn player time into materials and record nothing, so a session through them "
                + "reads as a session that never happened: " + silent);
    }
}
