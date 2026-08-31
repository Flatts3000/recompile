package com.flatts.recompile.content.worldgen.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 */
class RaggedRimTest {

    @ParameterizedTest
    @ValueSource(ints = {4, 6})
    @DisplayName("nothing survives above a hole")
    void nothingFloats(int raggedRows) {
        int floating = 0;
        int standing = 0;
        for (int x = -300; x < 300; x++) {
            for (int z = -300; z < 300; z += 5) {
                for (int fromTop = 0; fromTop < raggedRows; fromTop++) {
                    if (!RaggedRim.survives(x, z, fromTop, raggedRows)) {
                        continue;
                    }
                    standing++;
                    if (fromTop + 1 < raggedRows
                            && !RaggedRim.survives(x, z, fromTop + 1, raggedRows)) {
                        floating++;
                    }
                }
            }
        }
        assertTrue(standing > 1000, "the sample should contain rim; found " + standing);
        assertEquals(0, floating, floating + " of " + standing + " rim blocks had nothing beneath them");
    }

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
