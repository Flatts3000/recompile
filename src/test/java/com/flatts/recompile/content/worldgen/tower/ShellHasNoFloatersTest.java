package com.flatts.recompile.content.worldgen.tower;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Whether the cooling tower's shell actually leaves blocks hanging in the air.
 *
 * <p><b>This replaces a test that could not fail.</b> The first attempt asserted that the erosion rule
 * is monotonic up a column - but the rule is {@code fromTop >= eaten} with {@code eaten} depending only
 * on the column, so monotonicity is a restatement of the implementation rather than a property of the
 * structure. It would have passed against any change that kept that shape, including changes that put
 * blocks back in the sky.
 *
 * <p><b>And it was pointed at the wrong thing.</b> The erosion is not the only way to get a floater:
 * the shell is drawn as "within a tolerance of the radius", and above the throat the radius grows, so a
 * column can fall inside the band at one layer and outside it at the next. That is geometry, not
 * weathering, and no test of the erosion rule can see it.
 *
 * <p><b>And "touching nothing" was still the wrong question.</b> The first fix refused to place a block
 * whose six neighbours were all empty, which lets a detached PAIR through - two blocks holding each
 * other, floating clear of the tower - and a test asking whether anything touches nothing cannot see
 * them either. The owner could, from the ground.
 *
 * <p>The property is connectivity: <b>every block must trace back to the bottom course.</b> The piece
 * enforces that with a flood fill, and this <b>calls that fill</b> rather than reimplementing it - a
 * test that copies the algorithm it is checking is the same dead-code trap all over again.
 */
class ShellHasNoFloatersTest {

    @Test
    @DisplayName("the flood fill removes the detached blocks without eating the tower")
    void theFillIsLoadBearingAndNotOverzealous() {
        int[][] sizes = {{62, 15}, {68, 16}, {76, 18}};
        for (int[] size : sizes) {
            int height = size[0];
            double baseRadius = size[1];
            int span = (int) Math.ceil(baseRadius) + 1;
            int width = 2 * span + 1;

            // The raw shape, from the piece's own occupancy rule.
            int total = 0;
            for (int t = 0; t < height; t++) {
                for (int dx = -span; dx <= span; dx++) {
                    for (int dz = -span; dz <= span; dz++) {
                        if (CoolingTowerPiece.occupied(t, height, baseRadius, dx, dz, 0, 0, 0,
                                0, -1, -1, -1)) {
                            total++;
                        }
                    }
                }
            }
            assertTrue(total > 2000, "the shell should be substantial; found " + total);

            // AND WHAT THE PIECE ACTUALLY PLACES - the production fill, called rather than copied. A
            // test that reimplements the algorithm it is checking passes just as happily when the
            // real one is seeded from the wrong end, which is how the previous fix survived as dead
            // code.
            boolean[] rooted = CoolingTowerPiece.rooted(height, baseRadius, 0, 0, 0,
                0, -1, -1, -1, span);
            int reached = 0;
            for (boolean kept : rooted) {
                if (kept) {
                    reached++;
                }
            }

            int detached = total - reached;

            // THE FILL IS LOAD-BEARING. The raw geometry really does leave blocks that cannot reach the
            // ground - 29 of 4391 on the 62 tall tower when this was written - because the wall leans
            // and the radius sweeps past a column for a single layer. They come in clumps, which is why
            // the previous test, asking only whether a block touched anything, found two of them and
            // the owner found the rest by looking at the sky.
            assertTrue(detached > 0, "the raw geometry no longer detaches anything at " + height + "x"
                + baseRadius + ", so the flood fill may be dead code - check before deleting it");

            // AND IT MUST NOT EAT THE TOWER. A fill seeded or stepped wrongly would quietly discard
            // most of the shell, and the structure would still generate, just much less of it.
            assertTrue(reached > total * 0.99, "the fill kept only " + reached + " of " + total
                + " blocks, which is not a weathered rim, it is a broken tower");
        }
    }
}
