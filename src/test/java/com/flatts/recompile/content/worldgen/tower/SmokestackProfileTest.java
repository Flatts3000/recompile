package com.flatts.recompile.content.worldgen.tower;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How slender a smokestack is, which is the only thing that decides whether it reads as one.
 *
 * <p><b>This exists because the first version generated a keep.</b> At radius 3.4 and 25 to 40 tall
 * the ratio was about four to one, and it came out as a stubby brick tower standing among the yard's
 * steel frames. It generated cleanly and every test passed; only looking at it said anything was
 * wrong, which is the same way the cooling tower's flare was caught.
 *
 * <p>A real industrial chimney is nearer ten to one. There is no interior, no loot and no interaction
 * here, so the silhouette IS the feature and the ratio is its whole specification.
 */
class SmokestackProfileTest {

    @Test
    @DisplayName("even the shortest stack is slender enough to read as a chimney")
    void theShortestIsStillAChimney() {
        double ratio = SmokestackPiece.slenderness(SmokestackPiece.MIN_HEIGHT);
        assertTrue(ratio >= 5.5,
            "a stack this wide for its height reads as a tower, not a chimney; found "
            + String.format("%.1f", ratio) + " to 1");
    }

    @Test
    @DisplayName("it stays under the cooling tower, so the two landmarks layer")
    void itDoesNotCompeteWithTheTower() {
        // The tower rolls 62 to 76. If a stack could out-top it, the yard would have the tallest thing
        // in the world and the region-scale landmark would stop being the region-scale landmark.
        assertTrue(SmokestackPiece.MAX_HEIGHT < 62,
            "the tallest smokestack must stay below the shortest cooling tower, or the two compete");
    }
}
