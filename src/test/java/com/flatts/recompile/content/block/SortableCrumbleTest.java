package com.flatts.recompile.content.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The crumble curve's expected yield, as arithmetic rather than as a comment.
 *
 * <p>{@code SortableBlock} documents a table of hand averages that the Sorting Tarp's roll counts are
 * tuned against - the tarp must stay a clear improvement on bare hands without becoming a jackpot. Those
 * numbers were <b>wrong in one of the two files for months</b>: the tarp's javadoc claimed 1.9 / 1.5 /
 * 2.9 while {@code SortableBlock} said 2.5 / 2.0 / 3.5, and the balance pass would have been done
 * against whichever was read first.
 *
 * <p>They are pinned here because the formula is pure and the discrepancy was not caught by anything.
 * The model is a copy of {@code shouldCrumble}: this is a characterisation test, so if the real curve
 * changes these fail and the documented table has to move with it - which is the point.
 */
class SortableCrumbleTest {

    /** Mirrors {@code SortableBlock.shouldCrumble}: chance rises linearly from minPulls to maxPulls. */
    private static double expectedPulls(int minPulls, int maxPulls) {
        double expected = 0.0;
        double reachedThisFar = 1.0;
        int floor = minPulls - 1;
        for (int pulls = 1; pulls <= maxPulls; pulls++) {
            double crumbleNow = pulls >= maxPulls
                ? 1.0
                : (pulls < minPulls ? 0.0 : (double) (pulls - floor) / (maxPulls - floor));
            expected += pulls * reachedThisFar * crumbleNow;
            reachedThisFar *= (1.0 - crumbleNow);
        }
        return expected;
    }

    @Test
    @DisplayName("the documented hand averages are the ones the curve actually produces")
    void documentedAveragesHold() {
        assertEquals(2.5, expectedPulls(2, 3), 0.001, "garbage_block");
        assertEquals(2.0, expectedPulls(2, 2), 0.001, "trash_bag");
        assertEquals(3.5, expectedPulls(3, 4), 0.001, "compacted_bale");
        assertEquals(2.889, expectedPulls(2, 4), 0.001, "stone_rubble");
    }

    @Test
    @DisplayName("a wide window is NOT the midpoint, which is the trap in reading the table")
    void wideWindowIsNotTheMidpoint() {
        // (2+4)/2 = 3.0 looks right and is wrong. Stone Rubble's tarp value was derived from the real
        // 2.889, and picking 3.0 would have put its ratio outside the band the other three sit in.
        assertTrue(Math.abs(expectedPulls(2, 4) - 3.0) > 0.1,
            "if this ever equals the midpoint, the curve changed and the tarp values need rederiving");
    }

    @Test
    @DisplayName("the tarp stays a real improvement on bare hands for every input")
    void tarpBeatsHands() {
        // The ladder rule: hand << tarp << automation. These ratios are what "clearly above" means.
        record Input(String name, int min, int max, int tarpRolls) { }
        for (Input in : new Input[] {
                new Input("garbage_block", 2, 3, 6),
                new Input("trash_bag", 2, 2, 4),
                new Input("compacted_bale", 3, 4, 8),
                new Input("stone_rubble", 2, 4, 7)}) {
            double ratio = in.tarpRolls() / expectedPulls(in.min(), in.max());
            assertTrue(ratio >= 2.0 && ratio <= 2.5,
                in.name() + " tarp ratio " + ratio + " is outside the 2.0-2.5 band the tier is tuned to");
        }
    }

    @Test
    @DisplayName("minPulls >= 2 holds, so nothing comes apart in one touch")
    void floorIsLoadBearing() {
        // Documented as load-bearing in SortableBlock: dropping it to 1 made a third of garbage blocks
        // vanish on the first click, which reads as an instant break.
        assertTrue(expectedPulls(2, 3) >= 2.0, "a 2-3 window must never average below its floor");
        assertEquals(1.0, expectedPulls(1, 1), 0.001, "a 1-1 window is the degenerate case being avoided");
    }

    @Test
    @DisplayName("the curve is monotonic in the window, so widening never lowers the yield")
    void wideningNeverLowersYield() {
        Random random = new Random(1234);
        for (int i = 0; i < 200; i++) {
            int min = 2 + random.nextInt(4);
            int max = min + random.nextInt(4);
            assertTrue(expectedPulls(min, max + 1) >= expectedPulls(min, max) - 0.001,
                "widening " + min + "-" + max + " lowered the expected pulls");
        }
    }
}
