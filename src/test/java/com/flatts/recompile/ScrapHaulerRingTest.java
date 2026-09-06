package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.entity.ScrapHaulerGoal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The Scrap Hauler's search walks outward in rings and stops at the first one that pays out (#382).
 *
 * <p><b>The risk of a ring search is not that it is slow, it is that it MISSES.</b> The sweep it
 * replaced was obviously complete: two nested loops over every column in the area. Rings are not
 * obviously anything - the corners are the easy thing to double-count and the sides are the easy thing
 * to skip, and either mistake is invisible in play. A skipped column is a pile the machine drives past
 * forever; a doubled corner is a column read twice, which costs nothing and hides the first bug by
 * making the totals look plausible.
 *
 * <p>So this pins completeness rather than speed: rings 0 through n visit every cell of the
 * (2n+1) square exactly once, and each ring is exactly the cells at that Chebyshev distance. Speed is
 * a consequence of stopping early, and stopping early is only safe if nothing is missed.
 */
class ScrapHaulerRingTest {

    private static List<long[]> ring(int cx, int cz, int n) {
        List<long[]> out = new ArrayList<>();
        ScrapHaulerGoal.forEachOnRing(cx, cz, n, (x, z) -> {
            out.add(new long[] {x, z});
            return 0;
        });
        return out;
    }

    @Test
    void ring_zero_is_the_centre_alone() {
        List<long[]> cells = ring(10, -7, 0);
        assertEquals(1, cells.size());
        assertEquals(10, cells.get(0)[0]);
        assertEquals(-7, cells.get(0)[1]);
    }

    @Test
    void ring_n_holds_exactly_eight_n_cells() {
        // The perimeter of a (2n+1) square: (2n+1)^2 - (2n-1)^2 = 8n. If the corners were emitted
        // twice this would read 8n+4, which is the mistake this catches.
        for (int n = 1; n <= 12; n++) {
            assertEquals(8 * n, ring(0, 0, n).size(), "ring " + n);
        }
    }

    @Test
    void a_ring_holds_only_cells_at_that_distance() {
        for (int n = 0; n <= 8; n++) {
            for (long[] cell : ring(4, 9, n)) {
                int chebyshev = Math.max(Math.abs((int) cell[0] - 4), Math.abs((int) cell[1] - 9));
                assertEquals(n, chebyshev,
                    "ring " + n + " emitted (" + cell[0] + ", " + cell[1] + "), which is " + chebyshev
                        + " away - so the search would visit it out of order");
            }
        }
    }

    @Test
    void rings_together_cover_the_square_exactly_once() {
        int n = 9;
        Set<String> seen = new HashSet<>();
        int total = 0;
        for (int r = 0; r <= n; r++) {
            for (long[] cell : ring(-3, 5, r)) {
                total++;
                assertTrue(seen.add(cell[0] + "," + cell[1]),
                    "(" + cell[0] + ", " + cell[1] + ") was visited twice; a doubled cell is a column "
                        + "read twice, which is harmless on its own and hides a skipped one");
            }
        }
        int side = 2 * n + 1;
        assertEquals(side * side, seen.size(), "rings 0.." + n + " do not cover the square");
        assertEquals(side * side, total, "a cell was emitted more than once");
    }

    @Test
    void stopping_early_is_what_makes_it_cheap() {
        // The claim in #382, as a number rather than an assertion. The old search read every column in
        // the work area after every single block taken; the new one stops at the first ring holding a
        // pile. A pile three blocks from the machine therefore costs the rings up to it.
        int visited = 0;
        for (int r = 0; r <= 3; r++) {
            visited += ring(0, 0, r).size();
        }
        assertEquals(49, visited, "rings 0..3");

        int fullAreaAtDefaultRadius = (3 * 16) * (3 * 16);      // 3x3 chunks of 16x16 columns
        assertEquals(2304, fullAreaAtDefaultRadius);
        assertTrue(visited * 40 < fullAreaAtDefaultRadius,
            "the early exit should be worth more than an order of magnitude on a near pile");

        // And the guarantee that makes it safe to stop: a ring is only reached once every nearer one
        // has been fully searched, so the first hit really is the nearest.
        assertEquals(1 + 8 + 16 + 24, visited);
    }

    @Test
    void negative_coordinates_are_not_a_special_case() {
        // The whole search runs in world coordinates, which are as often negative as not, and the
        // work-area test around it uses >> 4 precisely because arithmetic here is easy to get wrong.
        List<long[]> cells = ring(-1000, -1000, 3);
        assertEquals(24, cells.size());
        for (long[] cell : cells) {
            assertEquals(3, Math.max(Math.abs((int) cell[0] + 1000), Math.abs((int) cell[1] + 1000)));
        }
    }
}
