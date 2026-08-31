package com.flatts.recompile.content.worldgen.tower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The tower's silhouette, measured rather than looked at.
 *
 * <p><b>This exists because the first one generated was wrong and only a screenshot said so.</b> The
 * throat sat at 0.72 of the height, which is where a real cooling tower's is, and the consequence was
 * that only a tenth of the height was left above it - so the top flared by about ten percent, which at
 * block resolution is nothing. It generated cleanly, it passed every test, and it read as a chimney.
 *
 * <p>The shape is pure arithmetic and it is the whole point of the structure, so the things that make
 * it recognisable are pinned here: it is widest at the ground, narrowest at the throat, and it opens
 * out again above that by enough to see from a distance. A future constant change that quietly
 * straightens it out fails here instead of shipping.
 */
class CoolingTowerProfileTest {

    /** A mid-range tower: 68 tall, base radius 16, which is what {@code boxFor} rolls around. */
    private static final int HEIGHT = 68;
    private static final double BASE = 16.0;

    private static double r(int t) {
        return CoolingTowerPiece.radiusAt(t, HEIGHT, BASE);
    }

    @Test
    @DisplayName("the curve passes through the base radius at the ground")
    void baseRadiusIsExact() {
        // Not decoration: the constant c is solved from this, so if it drifts the piece draws a shell
        // that does not meet its own bounding box and the wall is clipped at the chunk edge.
        assertEquals(BASE, r(0), 0.001);
    }

    @Test
    @DisplayName("it is narrowest at the throat and widest at the ground")
    void waistIsRealAndInTheRightPlace() {
        double narrowest = Double.MAX_VALUE;
        int narrowestAt = -1;
        for (int t = 0; t < HEIGHT; t++) {
            if (r(t) < narrowest) {
                narrowest = r(t);
                narrowestAt = t;
            }
        }
        assertTrue(r(0) > narrowest, "the ground should be wider than the throat");
        assertTrue(r(HEIGHT - 1) > narrowest, "the top should be wider than the throat");
        double where = narrowestAt / (double) (HEIGHT - 1);
        assertTrue(where > 0.45 && where < 0.75,
            "the throat should sit in the upper middle, not at either end; found it at " + where);
    }

    @Test
    @DisplayName("the top flares enough to see")
    void theFlareIsLegible() {
        double throat = Double.MAX_VALUE;
        for (int t = 0; t < HEIGHT; t++) {
            throat = Math.min(throat, r(t));
        }
        double flare = r(HEIGHT - 1) / throat;
        // THE ASSERTION THAT WOULD HAVE CAUGHT THE FIRST VERSION. It flared 1.11 and read as a chimney;
        // a quarter wider than the waist is what makes the shape legible at the distance this thing is
        // built to be seen from.
        assertTrue(flare > 1.25,
            "the top should open out at least a quarter wider than the throat, or it reads as a "
            + "chimney rather than a cooling tower; found " + String.format("%.2f", flare));
    }

    @Test
    @DisplayName("the wall never jumps more than a block per layer, so the shell has no seams")
    void theWallIsContinuous() {
        // The shell is drawn as "within a tolerance of the radius", and the tolerance widens with the
        // local slope. That only closes the seam while the slope stays modest; a profile that moved
        // several blocks per layer would leave holes the tolerance cannot cover.
        for (int t = 0; t < HEIGHT - 1; t++) {
            double step = Math.abs(r(t + 1) - r(t));
            assertTrue(step < 1.5,
                "radius jumped " + String.format("%.2f", step) + " blocks between layers " + t
                + " and " + (t + 1) + ", which the shell tolerance cannot bridge");
        }
    }
}
