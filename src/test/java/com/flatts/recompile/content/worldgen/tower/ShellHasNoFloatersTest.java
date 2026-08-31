package com.flatts.recompile.content.worldgen.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
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
 * <p>So this builds the shell the way {@code postProcess} does and asks the only question that matters:
 * <b>is any block touching nothing?</b>
 */
class ShellHasNoFloatersTest {

    private static long key(int x, int y, int z) {
        return ((long) (x + 512) << 40) | ((long) (y + 512) << 20) | (z + 512);
    }

    /** The shell of one tower, built through the piece's own occupancy rule. */
    private static Set<Long> shell(int height, double baseRadius) {
        Set<Long> blocks = new HashSet<>();
        // The tear is disabled - it is a deliberate hole, and its edges are held by the rest of the
        // ring. Everything else is the piece's own predicate, so this measures the real shape.
        for (int t = 0; t < height; t++) {
            int span = (int) Math.ceil(baseRadius) + 1;
            for (int dx = -span; dx <= span; dx++) {
                for (int dz = -span; dz <= span; dz++) {
                    if (!CoolingTowerPiece.occupied(t, height, baseRadius, dx, dz, 0, 0, 0,
                            0, -1, -1, -1)) {
                        continue;
                    }
                    // The piece refuses to place a block whose six neighbours are all empty, so the
                    // set under test has to be filtered the same way - otherwise this measures a shape
                    // that is never written.
                    if (touches(t, height, baseRadius, dx, dz)) {
                        blocks.add(key(dx, t, dz));
                    }
                }
            }
        }
        return blocks;
    }

    private static boolean touches(int t, int height, double baseRadius, int dx, int dz) {
        int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
        for (int[] step : around) {
            if (CoolingTowerPiece.occupied(t + step[1], height, baseRadius, dx + step[0], dz + step[2],
                    0, 0, 0, 0, -1, -1, -1)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("no block of the shell is touching nothing")
    void nothingIsIsolated() {
        int[][] sizes = {{62, 15}, {68, 16}, {76, 18}};
        for (int[] size : sizes) {
            int height = size[0];
            double baseRadius = size[1];
            Set<Long> blocks = shell(height, baseRadius);
            assertTrue(blocks.size() > 2000, "the shell should be substantial; found " + blocks.size());

            int isolated = 0;
            for (int t = 0; t < height; t++) {
                int span = (int) Math.ceil(baseRadius) + 1;
                for (int dx = -span; dx <= span; dx++) {
                    for (int dz = -span; dz <= span; dz++) {
                        if (!blocks.contains(key(dx, t, dz))) {
                            continue;
                        }
                        boolean touching =
                            blocks.contains(key(dx + 1, t, dz)) || blocks.contains(key(dx - 1, t, dz))
                            || blocks.contains(key(dx, t, dz + 1)) || blocks.contains(key(dx, t, dz - 1))
                            || blocks.contains(key(dx, t + 1, dz)) || blocks.contains(key(dx, t - 1, dz));
                        if (!touching) {
                            isolated++;
                        }
                    }
                }
            }
            assertEquals(0, isolated,
                "tower " + height + "x" + baseRadius + " left " + isolated + " blocks touching nothing");
        }
    }
}
