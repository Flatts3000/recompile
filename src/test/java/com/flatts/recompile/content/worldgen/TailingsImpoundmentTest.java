package com.flatts.recompile.content.worldgen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The tailings impoundment's geometry, which is pure arithmetic and so has no business being proven
 * in a world (#285, reshaped 2026-08-23).
 *
 * <p>Lives in the feature's own package rather than beside the other content tests, because the
 * geometry it measures is package-private. Widening it to public to suit a test would put four
 * accessors on the mod's surface that nothing in the mod calls.
 *
 * <p><b>Every one of these pins something that was actually wrong.</b> The feature's first shape
 * passed a full block census - tailings, stain and drums all present in the right proportions - and
 * still read as a field of cupcakes with a candle on each. The numbers could not see the shape.
 */
class TailingsImpoundmentTest {

    private static int[] radii() {
        int[] range = TailingsHeapFeature.radiusRange();
        int[] all = new int[range[1] - range[0] + 1];
        for (int i = 0; i < all.length; i++) {
            all[i] = range[0] + i;
        }
        return all;
    }

    private static int[] heights() {
        int[] range = TailingsHeapFeature.heightRange();
        int[] all = new int[range[1] - range[0] + 1];
        for (int i = 0; i < all.length; i++) {
            all[i] = range[0] + i;
        }
        return all;
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
     * A pond that reaches the rim is not contained. The plateau has to stand proud of the water on
     * every side or it stops being a basin and starts being a source block on a hilltop.
     */
    @Test
    void every_pond_keeps_a_rim_of_tailings_around_it() {
        for (int radius : radii()) {
            for (int height : heights()) {
                double pond = TailingsHeapFeature.pondRadius(radius, height);
                if (pond <= 0) {
                    continue;
                }
                double plateau = TailingsHeapFeature.plateauRadius(radius, height);
                assertTrue(pond <= plateau - 1.0,
                    "at radius " + radius + " height " + height + " the pond reaches " + pond
                        + " against a plateau of " + plateau + ", so there is under a block of rim "
                        + "holding it in. Water placed with no rim flows off the pile the first time "
                        + "anything updates it.");
            }
        }
    }

    /**
     * <b>The flat top must actually be flat, and the sides must actually slope.</b> The first shape
     * put the full height at the centre and ramped from there, which makes a cone with a one-block
     * spire - the thing the drum was then perched on. A plateau is what a real engineered impoundment
     * has, and it is what gives the pond somewhere to sit.
     */
    @Test
    void the_profile_is_a_plateau_with_a_sloped_skirt() {
        for (int radius : radii()) {
            for (int height : heights()) {
                double plateau = TailingsHeapFeature.plateauRadius(radius, height);
                assertTrue(plateau >= 2.0,
                    "radius " + radius + " height " + height + " leaves no flat top at all");
                assertTrue(plateau < radius,
                    "radius " + radius + " height " + height + " is plateau all the way to the edge ("
                        + plateau + "), which is a cylinder with a cliff for a side, not a spoil pile");
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
