package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which doors are open, asserted rather than assumed.
 *
 * <p><b>"Everything ships config-gated, but defaults are the design"</b> is the governing rule for
 * {@link RCConfig}, and until now nothing checked the two flags where that matters most. A repo-wide
 * grep for {@code NETHER_ENABLED} found the field and its one reader and nothing else - so a flip in
 * either direction was a silent one-character change to what the game is.
 *
 * <p><b>And the flip nearly landed as a no-op, which is the other half of why this exists.</b> A
 * config default only applies to a config file that does not exist yet: NeoForge rewrote the comment
 * in the existing dev config and kept the old value, so the change looked applied and did nothing.
 * That failure is invisible in a running game and invisible in a diff. This asserts the default the
 * mod ships with, which is the thing a fresh install actually gets.
 */
@DisplayName("dimension lockout defaults")
class DimensionDefaultsTest {

    @Test
    @DisplayName("the Nether ships open and the End ships shut")
    void dimensionDefaultsAreTheDesign() {
        // Owner ruling 2026-08-19: "Nether resources and progression are the reasons to go to the
        // Nether. Portals should be enabled." Until the themed generation lands this is the VANILLA
        // Nether, and RCDimensionLockout's javadoc lists what that routes around - iron and wood being
        // the two that matter most. Turning this back off is a design reversal, not a tuning tweak.
        assertTrue(RCConfig.NETHER_ENABLED.getDefault(),
            "the Nether ships OPEN; flipping this off reverses an owner ruling rather than tuning a "
                + "number, and it would also stop portal frames forming");

        // The End has no themed build and no ruling opening it. It stays shut, and the same flag
        // governs travel and portal formation so a frame cannot be lit into a wall.
        assertFalse(RCConfig.END_ENABLED.getDefault(),
            "the End ships SHUT until its themed build lands; opening it is a design decision that "
                + "belongs in the docs before it belongs in a default");
    }
}
