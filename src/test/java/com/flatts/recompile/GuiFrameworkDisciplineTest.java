package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A screen may not do its own drawing arithmetic.
 *
 * <p>This is issue #164's acceptance criterion 2 written down as a build failure: <i>no screen contains
 * a {@code RenderPipelines}, an atlas dimension, or a hardcoded slot coordinate</i>. Those three were the
 * cost that repeated on every screen the mod built - the 26.1 render model was re-learned four times, and
 * three screens ended up carrying a private {@code panel()} / {@code slot()} / {@code recess()} of their
 * own that approximated vanilla rather than borrowing it, so the same mod shipped two panels that did not
 * look alike.
 *
 * <p><b>{@code leftPos}/{@code topPos} is the load-bearing check</b>, and it is a proxy rather than a
 * literal reading of the criterion. "Hardcoded coordinate" is not something a regex can recognise - but
 * every hand-placed coordinate has to be added to the panel origin to mean anything, and the panel origin
 * has exactly two names. A screen that never mentions either cannot be positioning anything itself.
 *
 * <p>Scoped to screens on purpose. {@code VanillaGui} is the one class allowed to know a pipeline and an
 * atlas size, because being the single place that knows them is its whole job.
 */
class GuiFrameworkDisciplineTest {

    /** Things a screen must not name, and why each one means the screen is drawing by hand. */
    private static final String[][] FORBIDDEN = {
        {"RenderPipelines", "the render pipeline belongs to VanillaGui, which is the only class that blits"},
        {"blitSprite", "sprite drawing belongs to VanillaGui"},
        {".blit(", "texture drawing belongs to VanillaGui"},
        {"leftPos", "a screen that reads the panel origin is positioning something by hand"},
        {"topPos", "a screen that reads the panel origin is positioning something by hand"},
    };

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

    private static List<Path> screens() throws IOException {
        Path root = clientSources();
        assertTrue(Files.isDirectory(root), "client sources not found at " + root.toAbsolutePath());
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(f -> f.getFileName().toString().endsWith("Screen.java"))
                .filter(f -> !f.getFileName().toString().equals("LayoutScreen.java"))
                .sorted()
                .toList();
        }
    }

    @Test
    void every_screen_is_built_on_the_layout_framework() throws IOException {
        List<Path> screens = screens();
        // Four screens ship, and the count is the point: this test is worthless if it finds none.
        assertTrue(screens.size() >= 4, "expected the mod's four screens, found " + screens.size());

        List<String> wrong = new ArrayList<>();
        for (Path screen : screens) {
            String text = Files.readString(screen, StandardCharsets.UTF_8);
            if (!text.contains("extends LayoutScreen<")) {
                wrong.add(screen.getFileName() + " does not extend LayoutScreen");
            }
        }
        assertTrue(wrong.isEmpty(),
            "these screens bypass the layout framework, so their geometry is invisible to "
                + "MenuLayoutTests:\n  " + String.join("\n  ", wrong));
    }

    @Test
    void no_screen_does_its_own_drawing_arithmetic() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Path screen : screens()) {
            String text = Files.readString(screen, StandardCharsets.UTF_8);
            for (String[] rule : FORBIDDEN) {
                if (text.contains(rule[0])) {
                    wrong.add(screen.getFileName() + " mentions '" + rule[0] + "': " + rule[1]);
                }
            }
        }
        assertTrue(wrong.isEmpty(),
            "these screens draw by hand instead of through the layout:\n  "
                + String.join("\n  ", wrong));
    }
}
