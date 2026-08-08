package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Bay and the Burner Generator, the whole slot palette in {@code VanillaGui} <i>and</i> in two screens
 * that never called it, and the panel body in two more. They agreed only because they were
 * copy-pasted on one afternoon. Change the power red in one gauge and two machines quietly disagree
 * about what power looks like - and nothing tests colour, so it ships.
 *
 * <p>The rule is therefore "a colour has one home", not "no literals anywhere". A one-off tone used
 * by a single screen is fine; the same tone in two screens is the bug. {@code VanillaGui} is the home,
 * so a value naming it there and used from there appears exactly once.
 *
 * <p>Deliberately a source-text check rather than a reflective one. The colours are compile-time
 * constants, so by the time they are loadable they have already been inlined and the duplication is
 * invisible - the only place it exists is the text.
 */
class GuiColourConsistencyTest {

    private static final Pattern COLOUR = Pattern.compile("0x[0-9A-Fa-f]{8}");

    private static Path clientSources() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src").resolve("main").resolve("java")
                .resolve("com").resolve("flatts").resolve("recompile").resolve("client");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return Path.of("src", "main", "java", "com", "flatts", "recompile", "client");
    }

    @Test
    void no_gui_colour_is_written_in_two_files() throws IOException {
        Path root = clientSources();
        // moddev runs this layer with the working directory at build/minecraft-junit, so a relative
        // path resolves to nothing - and a scan over nothing finds no duplicates and passes forever.
        assertTrue(Files.isDirectory(root),
            "client sources not found at " + root.toAbsolutePath());

        Map<String, Set<String>> homes = new LinkedHashMap<>();
        int scanned = 0;
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                scanned++;
                String text = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = COLOUR.matcher(text);
                while (m.find()) {
                    homes.computeIfAbsent(m.group().toUpperCase(java.util.Locale.ROOT),
                        k -> new TreeSet<>()).add(file.getFileName().toString());
                }
            }
        }
        assertTrue(scanned > 3, "expected several client source files, scanned " + scanned);

        List<String> shared = new ArrayList<>();
        homes.forEach((colour, files) -> {
            if (files.size() > 1) {
                shared.add(colour + " in " + files);
            }
        });

        assertTrue(shared.isEmpty(),
            "these GUI colours are written in more than one file, so they agree only by luck - "
                + "give each a name in VanillaGui and use it:\n  " + String.join("\n  ", shared));
    }
}
