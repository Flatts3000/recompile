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
 * An entity texture is either drawn or it is a build input, and a build input must not ship.
 *
 * <p><b>This is a measured failure.</b> {@code scrap_hauler_plating.png} shipped from v0.19.0 at
 * 526,990 bytes - the single largest file in a 3.3MB jar, more than four times the next largest
 * asset - and no renderer has ever named it. Excluding it took the download from 3.3MB to 2.8MB.
 *
 * <p><b>It is not dead art, and the first version of this test assumed it was.</b> It is the
 * {@code material} of the procedural {@code scrap_hauler_skin} surface: texgen's entity styles sample
 * an AI surface for grain, wear and rivets over a layout the code controls to the pixel, and
 * {@code ProceduralBackend._material} resolves that by reading the PROMOTED png out of the assets
 * tree, raising if it is absent. So the file has to stay exactly where it is or
 * {@code texgen promote --surface scrap_hauler_skin} fails outright. Deleting it was one command away
 * from happening and would have broken regeneration silently until the next person tried it.
 *
 * <p>Hence two obligations rather than one, and they pull in opposite directions:
 *
 * <ul>
 *   <li>a texture no renderer names must be declared as some surface's {@code material}, or it really
 *       is dead weight;
 *   <li>a texture that is only a material must be excluded from the jar in {@code build.gradle}, or it
 *       is downloaded by everyone and drawn for nobody.
 * </ul>
 *
 * <p><b>Nothing existing could have caught either half.</b> {@code texgen validate} warns the other
 * way round - a texture no SURFACE claims - and this file had a surface, so it was silent.
 * {@code RegistryCompletenessTests} walks items and blocks, and an entity texture is neither.
 *
 * <p><b>Scoped to entity textures deliberately.</b> They are the one asset class where "does code read
 * this" is cheaply decidable: a block texture is reached through model JSON and an item texture
 * through a client item definition, so a regex over Java would call every one of those an orphan.
 */
class EntityTextureReachabilityTest {

    private static Path repoRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("src").resolve("main").resolve("java"))) {
                return dir;
            }
        }
        return Path.of("");
    }

    private static String readAllJava(Path root) throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(root.resolve("src").resolve("main").resolve("java"))) {
            for (Path java : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                all.append(Files.readString(java, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return all.toString();
    }

    private static List<Path> entityTextures(Path root) throws IOException {
        Path dir = root.resolve("src").resolve("main").resolve("resources")
            .resolve("assets").resolve("recompile").resolve("textures").resolve("entity");
        assertTrue(Files.isDirectory(dir), "entity textures not found at " + dir.toAbsolutePath());
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".png")).sorted().toList();
        }
    }

    @Test
    void every_entity_texture_is_either_drawn_or_a_declared_material() throws IOException {
        Path root = repoRoot();
        List<Path> textures = entityTextures(root);
        // The mod ships a pigeon, a roach, the Hauler's skin and its material. A run that finds none
        // is a moved directory rather than a clean bill of health, and would pass forever.
        assertTrue(textures.size() >= 3,
            "expected the mod's entity textures, found " + textures.size());

        String java = readAllJava(root);
        String manifest = Files.readString(root.resolve("texgen.toml"), StandardCharsets.UTF_8);
        String gradle = Files.readString(root.resolve("build.gradle"), StandardCharsets.UTF_8);

        // Prove each haystack is real before concluding anything is missing from it. Without this the
        // whole test passes by reading three empty strings.
        assertTrue(java.contains("textures/entity/"),
            "no source names textures/entity/ - the java scan is broken");
        assertTrue(manifest.contains("[surface."),
            "texgen.toml has no surfaces - the manifest scan is broken");
        assertTrue(gradle.contains("processResources"),
            "build.gradle has no processResources - the exclude scan is broken");

        List<String> problems = new ArrayList<>();
        for (Path texture : textures) {
            String file = texture.getFileName().toString();
            String surface = file.substring(0, file.length() - ".png".length());
            boolean drawn = java.contains("textures/entity/" + file);
            boolean material = manifest.contains("material = \"" + surface + "\"");
            boolean excluded = gradle.contains("assets/recompile/textures/entity/" + file);
            long bytes = Files.size(texture);

            if (!drawn && !material) {
                problems.add(file + " (" + bytes + " bytes) is named by no renderer and is no "
                    + "surface's material, so nothing reads it at runtime OR at generation time - "
                    + "wire it up or delete it");
            } else if (!drawn && !excluded) {
                problems.add(file + " (" + bytes + " bytes) is a texgen material rather than a "
                    + "drawn texture, so it must be excluded from the jar in build.gradle's "
                    + "processResources - otherwise every download carries art nobody sees");
            } else if (drawn && excluded) {
                problems.add(file + " is excluded from the jar but a renderer names it, so the "
                    + "entity will render as the missing texture in a built jar while looking "
                    + "correct in dev");
            }
        }
        assertTrue(problems.isEmpty(), String.join("; ", problems));
    }
}
