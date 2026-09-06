package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.block.entity.HaulerDepotBlockEntity;
import com.flatts.recompile.content.entity.ScrapHaulerGoal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * The Scrap Hauler's search walks outward in rings from the machine (#382).
 *
 * <p><b>The risk of a ring search is not that it is slow, it is that it MISSES.</b> The sweep it
 * replaced was obviously complete: two nested loops over every column in the area. Rings are not
 * obviously anything - the corners are the easy thing to double-count and the sides the easy thing to
 * skip, and either mistake is invisible in play. A skipped column is a pile the machine drives past
 * forever; a doubled corner costs nothing and makes the totals look plausible enough to hide the first
 * bug.
 *
 * <p><b>Two separate pieces can drop a column, and the first draft of this file only tested one.</b>
 * The ring walker is the mechanical half. The other is the BOUND - how many rings are needed to reach
 * the whole work area - and a bound written with one of its four terms missing would silently skip
 * everything on one side of the machine while passing every test about ring shape. Both are pure
 * statics for that reason, and both are pinned here.
 */
class ScrapHaulerRingTest {

    /** A box wide enough that clipping never bites, for the tests about ring shape alone. */
    private static final int WIDE = 1 << 20;

    private static List<long[]> ring(int cx, int cz, int n) {
        return ring(cx, cz, n, -WIDE, WIDE, -WIDE, WIDE);
    }

    private static List<long[]> ring(int cx, int cz, int n, int minX, int maxX, int minZ, int maxZ) {
        List<long[]> out = new ArrayList<>();
        ScrapHaulerGoal.forEachOnRing(cx, cz, n, minX, maxX, minZ, maxZ, (x, z) -> {
            out.add(new long[] {x, z});
            return 0;
        });
        return out;
    }

    // ---- the walker ---------------------------------------------------------------------

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
    void negative_coordinates_are_not_a_special_case() {
        List<long[]> cells = ring(-1000, -1000, 3);
        assertEquals(24, cells.size());
        for (long[] cell : cells) {
            assertEquals(3, Math.max(Math.abs((int) cell[0] + 1000), Math.abs((int) cell[1] + 1000)));
        }
    }

    // ---- the clip -----------------------------------------------------------------------

    @Test
    void clipping_yields_exactly_the_ring_inside_the_box() {
        // Clipping is an optimisation, so the only thing it may do is drop cells that are outside the
        // box. Anything else is a miss, which is the failure this whole file is about.
        int minX = -5, maxX = 12, minZ = 3, maxZ = 20;
        for (int n = 0; n <= 25; n++) {
            Set<String> clipped = new HashSet<>();
            for (long[] c : ring(2, 8, n, minX, maxX, minZ, maxZ)) {
                assertTrue(c[0] >= minX && c[0] <= maxX && c[1] >= minZ && c[1] <= maxZ,
                    "clipped ring emitted (" + c[0] + ", " + c[1] + "), which is outside the box");
                assertTrue(clipped.add(c[0] + "," + c[1]), "clipped ring emitted a cell twice");
            }
            Set<String> expected = new HashSet<>();
            for (long[] c : ring(2, 8, n)) {
                if (c[0] >= minX && c[0] <= maxX && c[1] >= minZ && c[1] <= maxZ) {
                    expected.add(c[0] + "," + c[1]);
                }
            }
            assertEquals(expected, clipped, "ring " + n + " clipped wrongly");
        }
    }

    @Test
    void a_machine_outside_its_own_area_costs_nothing_per_ring() {
        // The radius is adjustable from the Depot screen while the Hauler is out, so it can find itself
        // well outside the box it is meant to work. Every ring that misses the box entirely must emit
        // nothing rather than 8n cells to be rejected one at a time.
        int emitted = 0;
        for (int n = 0; n <= 60; n++) {
            emitted += ring(500, 500, n, -20, 20, -20, 20).size();
        }
        assertEquals(0, emitted, "a ring that cannot touch the box should emit nothing at all");
    }

    // ---- the bound ----------------------------------------------------------------------

    @Test
    void the_bound_reaches_every_column_of_the_work_area() {
        // The piece a shape test cannot catch. A bound missing one of its four terms skips everything
        // on one side of the machine, silently, and every test above still passes.
        BlockPos home = new BlockPos(37, 70, -114);
        for (int radius = 0; radius <= 3; radius++) {
            int minX = ((home.getX() >> 4) - radius) << 4;
            int maxX = (((home.getX() >> 4) + radius) << 4) + 15;
            int minZ = ((home.getZ() >> 4) - radius) << 4;
            int maxZ = (((home.getZ() >> 4) + radius) << 4) + 15;
            int columns = (maxX - minX + 1) * (maxZ - minZ + 1);

            // From each corner and the middle, which is where an asymmetric bound goes wrong.
            for (BlockPos self : List.of(new BlockPos(minX, 70, minZ), new BlockPos(maxX, 70, maxZ),
                    new BlockPos(minX, 70, maxZ), new BlockPos(maxX, 70, minZ), home,
                    new BlockPos(maxX + 300, 70, minZ - 250))) {
                Set<String> seen = new HashSet<>();
                int rings = ScrapHaulerGoal.ringsToCover(home, radius, self);
                for (int n = 0; n <= rings; n++) {
                    for (long[] c : ring(self.getX(), self.getZ(), n, minX, maxX, minZ, maxZ)) {
                        seen.add(c[0] + "," + c[1]);
                    }
                }
                assertEquals(columns, seen.size(),
                    "radius " + radius + " from " + self.toShortString() + ": the bound of " + rings
                        + " rings left " + (columns - seen.size()) + " column(s) unsearched");
            }
        }
    }

    @Test
    void stopping_early_is_what_makes_it_cheap() {
        // The claim in #382, as a number rather than an assertion. The old search read every column in
        // the work area after every block taken; the new one stops once the rings pass the best hit.
        int visited = 0;
        for (int r = 0; r <= 3; r++) {
            visited += ring(0, 0, r).size();
        }
        assertEquals(1 + 8 + 16 + 24, visited);
        assertEquals(49, visited, "rings 0..3");

        // Derived from the shipped default rather than typed, so this keeps describing the real
        // default if that ever moves.
        int side = (2 * HaulerDepotBlockEntity.DEFAULT_CHUNK_RADIUS + 1) * 16;
        int fullArea = side * side;
        assertEquals(2304, fullArea, "the default work area, in columns");
        assertTrue(visited * 40 < fullArea,
            "the early exit should be worth more than an order of magnitude on a near pile");
    }
}
