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
 * <p><b>A third thing, found in review: the rotation has to turn the right way.</b> Vanilla bakes an
 * element rotation as {@code Axis.YP.rotationDegrees(angle)} and friends, and the first version of
 * this computed {@code R(-angle)} on the X and Y planes by pairing the two in-plane coordinates in
 * the wrong order. It measured correctly anyway, for a reason that would not have lasted: both
 * rotated cases in this mod are sign-invariant - the Pump's rotated element is a square centred on
 * its own rotation origin, and the tire's four are a 4-fold-symmetric set, so they measure 69.13 at
 * +45 and at -45 alike. An element rotated about an origin off its own centre would have had its
 * mirror image measured, which is the vacuous pass the bullets above warn about arriving by a
 * different door.
 *
 * <p>Two things are still not modelled, neither of them used by any model here: a rotation about an
 * axis other than the plane's own tilts that plane out of true, so those pairs are skipped rather
 * than guessed at, and {@code "rescale": true} would widen a footprint by {@code 1/cos(angle)} and is
 * ignored. Both are conservative - they can miss a fight, they cannot invent one.
 */
class ModelFacesDoNotZFightTest {

    /** Below this, two planes are far enough apart for the depth buffer at any sane range. */
    private static final double PLANE_EPSILON = 1e-9;

    /** Ignore a sliver this small: floating-point clipping noise, not a face a player can see. */
    private static final double AREA_EPSILON = 1e-6;

    /**
     * <b>A ratchet, not an amnesty.</b> Two models still carry doubled faces where the members of a
     * flush lattice cross - a rim rail passing a corner post, a rail passing a mullion - and each
     * crossing doubles a square pixel or two on the shared outer plane. They are listed with the
     * area measured on 2026-09-07, <b>keyed by model AND axis</b>, so the sweep still fails on a new
     * model, on a new axis of a listed one, or on any listed number growing. Keyed by model alone it
     * would not: a file could take a regression on one plane, shed the same area on another, and sit
     * under its total unchanged, which is the drift the list exists to stop.
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
        "block/rain_collector_base.json|east/west", 24.00,
        "block/rain_collector_base.json|north/south", 26.40,
        "block/water_tank.json|east/west", 24.00,
        "block/water_tank.json|north/south", 26.40);

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
        Y(2, 0, 1, "up/down", "down", "up"),
        X(1, 2, 0, "east/west", "west", "east"),
        Z(0, 1, 2, "north/south", "north", "south");

        final int u;
        final int v;
        final int normal;
        final String faces;
        /** The face key an element must declare to draw on the low plane, and on the high one. */
        final String nearFace;
        final String farFace;

        Axis(int u, int v, int normal, String faces, String nearFace, String farFace) {
            this.u = u;
            this.v = v;
            this.normal = normal;
            this.faces = faces;
            this.nearFace = nearFace;
            this.farFace = farFace;
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
                // PER AXIS, not per file. Summing the three would let a model trade a regression on
                // one plane against an improvement on another and stay under a total, which is
                // exactly the drift an allowance list exists to stop.
                for (Axis axis : Axis.values()) {
                    double area = overlap(elements, axis);
                    double allowed = LATTICE_CROSSINGS.getOrDefault(id + "|" + axis.faces, 0.0);
                    if (area > allowed + AREA_EPSILON) {
                        problems.add(String.format("%s: %.2f sq px of doubled %s faces%s", id, area,
                            axis.faces,
                            allowed > 0 ? String.format(" - allowed %.2f", allowed) : ""));
                    }
                    // A listed model that gets BETTER does not fail, but the entry now overstates
                    // it, so say so rather than leaving the slack behind.
                    if (allowed > 0 && area < allowed - AREA_EPSILON) {
                        problems.add(String.format(
                            "%s is down to %.2f sq px of %s faces but LATTICE_CROSSINGS still allows "
                                + "%.2f - lower the entry, or delete it if it is now zero. A stale "
                                + "allowance is a hole the next regression falls through.",
                            id, area, axis.faces, allowed));
                    }
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

    /**
     * <b>A shared plane is only a fight if both elements actually DRAW there.</b> An element's
     * {@code faces} map is not required to hold all six, and omitting one is how a modeller says "this
     * side is inside something else, do not draw it" - so two elements can share a plane with at most
     * one quad on it, which cannot fight anything.
     *
     * <p>This was missing at first and it did real damage rather than just over-reporting. The compost
     * heap's floor slab declares only {@code down} and {@code up} while its corner posts declare no
     * {@code down} at all, so the 32 square pixels the sweep charged it were entirely phantom - and
     * the "fix" for them raised the posts off the floor, which deleted the only side faces drawn in
     * the block's bottom pixel and opened a see-through band all the way round. A false positive in a
     * build-failing test does not stay a false positive; somebody makes the model worse to satisfy it.
     */
    private static boolean draws(JsonObject element, String face) {
        return element.has("faces") && element.getAsJsonObject("faces").has(face);
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
                JsonObject a = elements.get(i).getAsJsonObject();
                JsonObject b = elements.get(j).getAsJsonObject();
                boolean nearShared = Math.abs(first.near() - second.near()) <= PLANE_EPSILON
                    && draws(a, axis.nearFace) && draws(b, axis.nearFace);
                boolean farShared = Math.abs(first.far() - second.far()) <= PLANE_EPSILON
                    && draws(a, axis.farFace) && draws(b, axis.farFace);
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
