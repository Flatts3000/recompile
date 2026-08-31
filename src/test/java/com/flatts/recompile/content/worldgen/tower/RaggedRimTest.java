package com.flatts.recompile.content.worldgen.tower;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The rule that keeps weathered rims off the ceiling.
 *
 * <p><b>Both structures shipped this wrong, separately.</b> Rolling each block of the top courses on
 * its own is the natural way to write "more of it is gone the higher you go", and it leaves bricks
 * hovering over the structure - which on a landmark, whose whole feature is its silhouette, is the
 * thing broken rather than a blemish. The cooling tower's was caught in review; the smokestack had
 * the same arithmetic and the same effect.
 *
 * <p>Row counts are parameterised because the two structures weather different depths, and the
 * property has to hold for both.
 *
 * <p><b>What this does NOT test is whether anything floats.</b> A first version asserted that erosion
 * is monotonic up a column, which is a restatement of {@code fromTop >= eaten} rather than a property -
 * it could not fail. Floating is a question about the assembled shell, including the geometry that has
 * nothing to do with weathering, and it lives in {@code ShellHasNoFloatersTest}.
 */
class RaggedRimTest {

    @ParameterizedTest
    @ValueSource(ints = {4, 6})
    @DisplayName("the rim is ragged, not dissolved")
    void theTopRowStillExists(int raggedRows) {
        int survived = 0;
        int total = 0;
        for (int x = -300; x < 300; x++) {
            for (int z = -300; z < 300; z += 5) {
                total++;
                if (RaggedRim.survives(x, z, 0, raggedRows)) {
                    survived++;
                }
            }
        }
        double rate = survived / (double) total;
        // The first version bit 86% out of the top course, which reads as confetti rather than as
        // weathering. Enough has to remain that the rim is still a rim.
        assertTrue(rate > 0.15, "only " + Math.round(rate * 100) + "% of the top course survived");
    }
}
