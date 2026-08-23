package com.flatts.recompile.content.worldgen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The tailings impoundment's geometry, which is pure arithmetic and so has no business being proven in
 * a world (#285, reshaped 2026-08-23).
 *
 * <p>Lives in the feature's own package rather than beside the other content tests, because the
 * geometry it measures is package-private. Widening it to public to suit a test would put accessors on
 * the mod's surface that nothing in the mod calls.
 *
 * <p><b>Every one of these pins something that was actually wrong.</b> The feature's first shape passed
 * a full block census - tailings, stain and drums all present in the right proportions - and still read
 * as a field of cupcakes with a candle on each. The numbers could not see the shape.
 *
 * <p>Two of these assertions were themselves rewritten in review: they restated the implementation
 * rather than measuring it. {@code plateauRadius} is a {@code Math.max(2.0, ...)}, so asserting the
 * plateau is at least 2 could never fail; {@code pondRadius} is defined as the plateau minus a fixed
 * rim, so asserting the rim exists could never fail either. Both now measure {@link
 * TailingsHeapFeature#columnAt} instead, which is the function that actually decides the silhouette.
 */
class TailingsImpoundmentTest {

    private static int[] span(int[] range) {
        int[] all = new int[range[1] - range[0] + 1];
        for (int i = 0; i < all.length; i++) {
            all[i] = range[0] + i;
        }
        return all;
    }

    private static int[] radii() {
        return span(TailingsHeapFeature.radiusRange());
    }

    private static int[] heights() {
        return span(TailingsHeapFeature.heightRange());
    }

    /**
     * <b>The hard one: a feature may not write outside its own chunk and its eight neighbours.</b>
     * {@code ChunkPyramid} gives {@code ChunkStatus.FEATURES} a {@code blockStateWriteRadius(1)} and
     * {@code WorldGenRegion.ensureCanWrite} compares chunk coordinates against it, so with
     * {@code minecraft:in_square} putting the origin anywhere in the centre chunk's local 0-15, only 16
     * blocks in each direction are guaranteed writable.
     *
     * <p>Past that the write is rejected and logged at ERROR, once per block, and the pile comes out
     * sheared flat along a chunk line. The first draft of the impoundment rewrite had radius 16 with
     * drums thrown to 23 and would have done precisely that. Nothing in the test suite could see it,
     * because the block census only counts what was successfully placed.
     */
    @Test
    void the_feature_never_writes_outside_its_allowed_chunks() {
        double footprint = TailingsHeapFeature.maxFootprint();
        assertTrue(footprint <= TailingsHeapFeature.MAX_REACH,
            "the widest possible pile reaches " + String.format("%.2f", footprint) + " blocks from its "
                + "origin (longest lobe plus the stain ring) against a hard engine limit of "
                + TailingsHeapFeature.MAX_REACH + ". Every block past that is silently REJECTED by "
                + "WorldGenRegion.ensureCanWrite and logged at ERROR, and the pile is cut flat along "
                + "the chunk boundary. Reduce MAX_RADIUS, the lobe amplitudes, or the stain ring.");
        assertTrue(!TailingsHeapFeature.writable(TailingsHeapFeature.MAX_REACH + 1, 0),
            "the writable() backstop admits an offset past MAX_REACH, so it is not backstopping");
        assertTrue(TailingsHeapFeature.writable(TailingsHeapFeature.MAX_REACH, 0),
            "the writable() backstop rejects an offset that is legal, so it would shear a valid pile");
    }

    /**
     * <b>The decant pond is the thing that silently did not generate.</b> With the first radius and
     * height ranges the skirt ate almost the whole footprint at the angle of repose, so the plateau
     * came out under the pond threshold about three times in four - and nothing failed, because a pile
     * with no pond is a perfectly valid pile. It took censusing a real world and finding zero water
     * anywhere to notice.
     *
     * <p>The pond is the single most recognisable thing in a photograph of a uranium mill site, so it
     * has to be the common case rather than a bonus.
     */
    @Test
    void a_decant_pond_is_not_a_coin_flip() {
        int total = 0;
        int withPond = 0;
        StringBuilder dry = new StringBuilder();
        for (int radius : radii()) {
            for (int height : heights()) {
                total++;
                if (TailingsHeapFeature.pondRadius(radius, height) > 0) {
                    withPond++;
                } else {
                    dry.append(" r").append(radius).append("/h").append(height);
                }
            }
        }
        int percent = withPond * 100 / total;
        assertTrue(percent >= 80,
            "only " + percent + "% of (radius, height) combinations leave a plateau big enough for a "
                + "decant pond (" + withPond + " of " + total + "). Dry combinations:" + dry
                + ". The pond is what makes this read as a mill site rather than as a slag heap; it "
                + "cannot be a minority outcome. If the skirt is eating the footprint, the pile is too "
                + "TALL for its width - real impoundments are near 1:12, not 1:4.");
    }

    /**
     * <b>The flat top must actually be flat, and the sides must actually slope.</b> Measured over
     * {@link TailingsHeapFeature#columnAt}, which is the function that draws the silhouette, rather
     * than over the radius that feeds it.
     *
     * <p>The first shape put the full height at the centre and ramped from there, which makes a cone
     * with a one-block spire - the thing the drum was then perched on. A real engineered impoundment
     * has a plateau, and the plateau is what gives the pond somewhere to sit.
     */
    @Test
    void the_profile_is_a_plateau_with_a_sloped_skirt() {
        for (int radius : radii()) {
            for (int height : heights()) {
                double plateau = TailingsHeapFeature.plateauRadius(radius, height);
                double edge = radius;

                // The top is flat: every sample inside the plateau is at full height.
                for (double d = 0.0; d <= plateau; d += 0.25) {
                    int column = TailingsHeapFeature.columnAt(d, plateau, edge, height);
                    assertTrue(column == height,
                        "r" + radius + "/h" + height + ": the top is not flat - at distance " + d
                            + " the column is " + column + " rather than " + height);
                }

                // The plateau is a real share of the pile, not a token. A one-block cap with a ramp
                // off it is the cone this rewrite exists to remove, and Math.max(2.0, ...) will hand
                // one back silently if the ranges are ever retuned so the skirt eats everything.
                assertTrue(plateau >= radius * 0.25,
                    "r" + radius + "/h" + height + ": the flat top is only " + plateau + " against a "
                        + "radius of " + radius + ", so this is a cone with a nub on it rather than an "
                        + "impoundment. The skirt is taking too much - the pile is too tall.");

                // The skirt descends the whole way and reaches the ground at the edge.
                int previous = height;
                for (double d = plateau; d <= edge; d += 0.25) {
                    int column = TailingsHeapFeature.columnAt(d, plateau, edge, height);
                    assertTrue(column <= previous,
                        "r" + radius + "/h" + height + ": the skirt rises again at distance " + d
                            + " (" + column + " after " + previous + ")");
                    previous = column;
                }
                assertTrue(TailingsHeapFeature.columnAt(edge, plateau, edge, height) == 0,
                    "r" + radius + "/h" + height + ": the skirt does not reach the ground at the edge, "
                        + "so the pile ends in a step instead of a slope");
            }
        }
    }

    /**
     * Broad and low, not tall and narrow. This is the ratio that decides whether the thing reads as an
     * impoundment or as a cake, and it is the one the first version got wrong: Moab is roughly 1:12
     * across to high, and radius 8-12 against height 4-6 is nearer 1:4.
     */
    @Test
    void the_pile_is_far_wider_than_it_is_tall() {
        int[] radius = TailingsHeapFeature.radiusRange();
        int[] height = TailingsHeapFeature.heightRange();
        double worst = (2.0 * radius[0]) / height[1];
        assertTrue(worst >= 4.0,
            "the squattest possible pile is " + String.format("%.1f", worst) + ":1 wide to high "
                + "(width " + (2 * radius[0]) + ", height " + height[1] + "). Real tailings "
                + "impoundments are nearer 12:1. Anything under 4:1 reads as a mound rather than as "
                + "something engineered, which is what the first version of this feature looked like.");
    }
}
