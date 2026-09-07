package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * No two elements of a block model may put same-facing faces on the same plane.
 *
 * <p><b>The bug this is written from</b> (#404, owner screenshot 2026-09-07: the tops of a tire heap
 * flicker). A tire is an octagonal ring of four straight boxes plus the same four rotated 45 degrees,
 * and every element ran the full 0 to 8 - so where a flat segment met a diagonal, two {@code up}
 * faces sat on one plane and two {@code down} faces on another. Two faces at the same depth pointing
 * the same way is z-fighting by construction: the depth test cannot separate them, so which one wins
 * is decided per pixel per frame and the surface crawls.
 *
 * <p><b>Nothing else can see this.</b> A model with doubled faces loads, registers, resolves every
 * texture and passes {@code RegistryCompletenessTests}; both test layers are green while the block
 * renders wrong. The only detector before this test was somebody standing in front of it, which is
 * how the tires shipped in v0.18.0 and stayed for two releases, and how five more models were still
 * doing it when this was written.
 *
 * <p><b>Two things this has to get right, both learned the hard way in #404.</b>
 *
 * <ul>
 *   <li><b>Apply element rotation before comparing footprints.</b> The tire's overlap existed ONLY
 *       between an axis-aligned box and a 45-degree one; comparing raw {@code from}/{@code to} finds
 *       nothing at all and the test passes vacuously on the exact model it was written for.
 *   <li><b>Compare same-facing planes only</b> - top against top, bottom against bottom. An
 *       {@code up} face and a {@code down} face on one plane is the ordinary way two stacked elements
 *       meet and never fights, because back-face culling drops one of the two from every viewpoint.
 *       A test that flags those is unusable: it fires on almost every model in the mod.
 * </ul>
 *
 * <p>A rotation about an axis other than the plane's own tilts that plane out of true, so those pairs
 * are skipped rather than guessed at. That is deliberately conservative - it can miss a fight, it
 * cannot invent one - and no model in this mod uses one today.
 */
class ModelFacesDoNotZFightTest {

    /** Below this, two planes are far enough apart for the depth buffer at any sane range. */
    private static final double PLANE_EPSILON = 1e-9;

    /** Ignore a sliver this small: floating-point clipping noise, not a face a player can see. */
    private static final double AREA_EPSILON = 1e-6;

    /**
     * <b>A ratchet, not an amnesty.</b> Three models still carry doubled faces where the members of a
     * flush lattice cross - a rim rail passing a corner post, a rail passing a mullion - and each
     * crossing doubles a square pixel or two on the shared outer plane. They are listed with the
     * area measured on 2026-09-07, so the sweep still fails on a NEW model, on a new axis, or on any
     * of these three growing.
     *
     * <p><b>Why these are left and the others were not.</b> The cases fixed in this pass were
     * removable exactly: geometry that was redundant, so deleting it left the model's union
     * unchanged. Fixing a lattice crossing exactly means splitting every member at every crossing,
     * which multiplies the element count of a model for a square pixel each. <b>And they are the
     * milder half of the defect.</b> The two confirmed-visible cases - the tire heap the owner
     * screenshotted (#404) and the Pump - are a box overlapping a copy of ITSELF rotated 45 degrees,
     * so the two quads have different vertices and different triangulations and their interpolated
     * depths genuinely disagree in the low bits. A lattice crossing is two axis-aligned rectangles on
     * one plane, where the depth is analytically identical and only the rounding path differs. Both
     * of the severe cases are now at zero; nobody has yet reported seeing one of these.
     *
     * <p>So the honest state is recorded rather than papered over: <b>these want an in-game look
     * before anyone spends elements on them</b> (#405). If they turn out to be visible, split the
     * members; if not, delete the entry and leave a note saying it was checked.
     */
    private static final Map<String, Double> LATTICE_CROSSINGS = Map.of(
        "block/rain_collector_base.json", 50.40,
        "block/water_tank.json", 50.40,
        // The body's underside and the corner posts' undersides share y=1 - both sitting ON the floor
        // slab that spans 0..1 across the whole block, so neither face can be seen from any angle.
        "block/compost_heap.json", 4.00);

    private static Path resourceRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("src").resolve("main").resolve("resources");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        return Path.of("src", "main", "resources");
    }

    /** One element, reduced to what matters here: a footprint on a plane and the two plane offsets. */
    private record Slab(double[][] footprint, double near, double far) {
    }

    /**
     * The axis whose faces we are testing, as the two in-plane coordinate indices plus the normal.
     * {@code Y} is up/down, {@code X} is east/west, {@code Z} is north/south.
     */
    private enum Axis {
        Y(0, 2, 1, "up/down"),
        X(2, 1, 0, "east/west"),
        Z(0, 1, 2, "north/south");

        final int u;
        final int v;
        final int normal;
        final String faces;

        Axis(int u, int v, int normal, String faces) {
            this.u = u;
            this.v = v;
            this.normal = normal;
            this.faces = faces;
        }
    }

    private static double[] corner(JsonObject element, String key) {
        JsonArray array = element.getAsJsonArray(key);
        return new double[] {array.get(0).getAsDouble(), array.get(1).getAsDouble(),
            array.get(2).getAsDouble()};
    }

    /** Null when the element's rotation tilts this plane, which we decline to reason about. */
    private static Slab slab(JsonObject element, Axis axis) {
        double[] from = corner(element, "from");
        double[] to = corner(element, "to");
        double[][] points = {
            {from[axis.u], from[axis.v]}, {to[axis.u], from[axis.v]},
            {to[axis.u], to[axis.v]}, {from[axis.u], to[axis.v]},
        };
        if (element.has("rotation")) {
            JsonObject rotation = element.getAsJsonObject("rotation");
            if (!rotation.get("axis").getAsString().equalsIgnoreCase(axis.name())) {
                return null;
            }
            JsonArray originArray = rotation.getAsJsonArray("origin");
            double originU = originArray.get(axis.u).getAsDouble();
            double originV = originArray.get(axis.v).getAsDouble();
            double angle = Math.toRadians(rotation.get("angle").getAsDouble());
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            for (double[] point : points) {
                double du = point[0] - originU;
                double dv = point[1] - originV;
                point[0] = originU + du * cos - dv * sin;
                point[1] = originV + du * sin + dv * cos;
            }
        }
        return new Slab(wound(points), from[axis.normal], to[axis.normal]);
    }

    /** Counter-clockwise, so the clipper's half-plane test has a consistent sign. */
    private static double[][] wound(double[][] points) {
        double sum = 0;
        for (int i = 0; i < points.length; i++) {
            double[] a = points[i];
            double[] b = points[(i + 1) % points.length];
            sum += (b[0] - a[0]) * (b[1] + a[1]);
        }
        if (sum >= 0) {
            double[][] reversed = new double[points.length][];
            for (int i = 0; i < points.length; i++) {
                reversed[i] = points[points.length - 1 - i];
            }
            return reversed;
        }
        return points;
    }

    private static double signedArea(List<double[]> polygon) {
        double sum = 0;
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i);
            double[] b = polygon.get((i + 1) % polygon.size());
            sum += a[0] * b[1] - b[0] * a[1];
        }
        return Math.abs(sum) / 2;
    }

    /** Sutherland-Hodgman: the part of {@code subject} inside the convex {@code clipper}. */
    private static List<double[]> intersect(double[][] subject, double[][] clipper) {
        List<double[]> output = new ArrayList<>(List.of(subject));
        for (int i = 0; i < clipper.length; i++) {
            double[] a = clipper[i];
            double[] b = clipper[(i + 1) % clipper.length];
            List<double[]> input = output;
            output = new ArrayList<>();
            if (input.isEmpty()) {
                break;
            }
            for (int j = 0; j < input.size(); j++) {
                double[] current = input.get(j);
                double[] previous = input.get((j - 1 + input.size()) % input.size());
                double sideCurrent = side(a, b, current);
                double sidePrevious = side(a, b, previous);
                if (sideCurrent >= 0) {
                    if (sidePrevious < 0) {
                        output.add(cross(previous, current, sidePrevious, sideCurrent));
                    }
                    output.add(current);
                } else if (sidePrevious >= 0) {
                    output.add(cross(previous, current, sidePrevious, sideCurrent));
                }
            }
        }
        return output;
    }

    private static double side(double[] a, double[] b, double[] point) {
        return (b[0] - a[0]) * (point[1] - a[1]) - (b[1] - a[1]) * (point[0] - a[0]);
    }

    private static double[] cross(double[] from, double[] to, double sideFrom, double sideTo) {
        double t = sideFrom / (sideFrom - sideTo);
        return new double[] {from[0] + t * (to[0] - from[0]), from[1] + t * (to[1] - from[1])};
    }

    @Test
    void no_model_draws_two_same_facing_faces_on_one_plane() throws IOException {
        Path models = resourceRoot().resolve("assets").resolve("recompile").resolve("models");
        assertTrue(Files.isDirectory(models), "no models directory at " + models);

        List<String> problems = new ArrayList<>();
        int scanned = 0;
        int withElements = 0;
        try (Stream<Path> tree = Files.walk(models)) {
            List<Path> files = tree.filter(p -> p.toString().endsWith(".json")).sorted().toList();
            for (Path file : files) {
                scanned++;
                JsonObject model = JsonParser
                    .parseString(Files.readString(file, StandardCharsets.UTF_8))
                    .getAsJsonObject();
                if (!model.has("elements")) {
                    continue;
                }
                withElements++;
                JsonArray elements = model.getAsJsonArray("elements");
                String id = models.relativize(file).toString().replace('\\', '/');
                double allowed = LATTICE_CROSSINGS.getOrDefault(id, 0.0);
                double total = 0;
                List<String> perAxis = new ArrayList<>();
                for (Axis axis : Axis.values()) {
                    double area = overlap(elements, axis);
                    if (area > AREA_EPSILON) {
                        total += area;
                        perAxis.add(String.format("%.2f on %s", area, axis.faces));
                    }
                }
                // Strictly greater, so a listed model that gets BETTER does not fail - but the
                // entry then overstates it, so the message says to retighten the number.
                if (total > allowed + AREA_EPSILON) {
                    problems.add(String.format("%s: %.2f sq px doubled (%s)%s", id, total,
                        String.join(", ", perAxis),
                        allowed > 0 ? String.format(" - allowed %.2f", allowed) : ""));
                }
                if (allowed > 0 && total < allowed - AREA_EPSILON) {
                    problems.add(String.format(
                        "%s is down to %.2f sq px but LATTICE_CROSSINGS still allows %.2f - lower the "
                            + "entry to the new number, or delete it if it is now zero. A stale "
                            + "allowance is a hole the next regression falls through.",
                        id, total, allowed));
                }
            }
        }

        // A sweep that scans nothing passes for the wrong reason, and this repo has already paid for
        // one: the COVER pass's package scan found no classes and reported a clean bill of health.
        assertTrue(scanned > 100, "scanned only " + scanned + " model files - the walk found nothing");
        assertTrue(withElements > 10,
            "only " + withElements + " models had elements - nothing was actually checked");

        assertTrue(problems.isEmpty(),
            "Models draw two same-facing faces on one plane, which z-fights - the surface crawls as "
                + "the camera moves and no other test can see it:\n  "
                + String.join("\n  ", problems)
                + "\n\nFix it exactly where you can: shrink or move whichever element is redundant, "
                + "so the union of the model is unchanged. Where the geometry makes that impossible - "
                + "a box and a copy of it rotated 45 degrees cannot be trimmed apart, because the "
                + "intersection boundary is diagonal - shift one element by 0.25px. SHIFT rather than "
                + "inset: insetting both ends of an element opens a see-through slit wherever "
                + "something has to stay continuous through that plane. See TireBlock's javadoc.");
    }

    private static double overlap(JsonArray elements, Axis axis) {
        double total = 0;
        for (int i = 0; i < elements.size(); i++) {
            Slab first = slab(elements.get(i).getAsJsonObject(), axis);
            if (first == null) {
                continue;
            }
            for (int j = i + 1; j < elements.size(); j++) {
                Slab second = slab(elements.get(j).getAsJsonObject(), axis);
                if (second == null) {
                    continue;
                }
                boolean nearShared = Math.abs(first.near() - second.near()) <= PLANE_EPSILON;
                boolean farShared = Math.abs(first.far() - second.far()) <= PLANE_EPSILON;
                if (!nearShared && !farShared) {
                    continue;
                }
                List<double[]> shared = intersect(first.footprint(), second.footprint());
                if (shared.size() < 3) {
                    continue;
                }
                double area = signedArea(shared);
                if (nearShared) {
                    total += area;
                }
                if (farShared) {
                    total += area;
                }
            }
        }
        return total;
    }
}
