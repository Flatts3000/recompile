package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * No GUI colour may be written out in more than one file.
 *
 * <p><b>The defect this guards is divergence, not style.</b> Before it, the same seven values were
 * declared in two or three places each: {@code POWER} and {@code POWER_IDLE} in both the Hydroponics
 * Bay and the Burner Generator, the whole slot palette in the chrome class <i>and</i> in two screens
 * that never called it, and the panel body in two more. They agreed only because they were copy-pasted
 * on one afternoon. Change the power red in one gauge and two machines quietly disagree about what power
 * looks like - and nothing tests colour, so it ships.
 *
 * <p>The rule is therefore "a colour has one home", not "no literals anywhere". {@code GuiTheme} is the
 * home, so a value named there and used from there appears exactly once.
 *
 * <p>It found a second class of the same bug when the framework landed: the Tree Nursery drew its water
 * in {@code 0xFF3A78C2} and the Hydroponics Bay drew its water in {@code 0xFF3F76E4}. Two blues for one
 * substance, each a single-file literal, so this test had been silent about both. Naming them is what
 * made them one colour.
 *
 * <p>Deliberately a source-text check rather than a reflective one. The colours are compile-time
 * constants, so by the time they are loadable they have already been inlined and the duplication is
 * invisible - the only place it exists is the text.
 */
class GuiColourConsistencyTest {

    private static final Pattern COLOUR = Pattern.compile("0x[0-9A-Fa-f]{8}");

    /**
     * Where GUI code lives: the client half, and the common half the layouts are declared in.
     *
     * <p>Both, because the theme moved out of the client package when the framework landed - a layout has
     * to be able to name a colour and a layout may not load client classes. Scanning only the old root
     * would have quietly stopped covering the file that now holds every colour in the mod.
     */
    private static final String[] ROOTS = {"client", "gui"};

    private static Path sourceRoot() {
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
    void no_gui_colour_is_written_in_two_files() throws IOException {
        Path root = sourceRoot();
        // moddev runs this layer with the working directory at build/minecraft-junit, so a relative
        // path resolves to nothing - and a scan over nothing finds no duplicates and passes forever.
        assertTrue(Files.isDirectory(root), "sources not found at " + root.toAbsolutePath());

        Map<String, Set<String>> homes = new LinkedHashMap<>();
        int scanned = 0;
        for (String name : ROOTS) {
            Path dir = root.resolve(name);
            assertTrue(Files.isDirectory(dir), "expected a GUI source root at " + dir);
            // Walk, not list: the framework's client half is a subpackage, and a non-recursive scan
            // would silently stop covering every file that moved into it.
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    scanned++;
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    Matcher matcher = COLOUR.matcher(text);
                    while (matcher.find()) {
                        homes.computeIfAbsent(matcher.group().toUpperCase(Locale.ROOT),
                            key -> new TreeSet<>()).add(file.getFileName().toString());
                    }
                }
            }
        }
        assertTrue(scanned > 8, "expected several GUI source files, scanned " + scanned);

        List<String> shared = new ArrayList<>();
        homes.forEach((colour, files) -> {
            if (files.size() > 1) {
                shared.add(colour + " in " + files);
            }
        });

        assertTrue(shared.isEmpty(),
            "these GUI colours are written in more than one file, so they agree only by luck - "
                + "give each a name in GuiTheme and use it:\n  " + String.join("\n  ", shared));
    }
}
