package com.flatts.recompile.content.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pigeon's wingbeat has to stay bounded, and this is the only layer that can say so.
 *
 * <p><b>What went wrong.</b> The renderer set the flap angle to the raw {@code tickCount}, so it grew
 * without limit. Vanilla's parrot model spends that value as {@code bobbingBody = flapAngle * 0.3F} and
 * adds it to the {@code y} of every part, and {@code +y} is <em>down</em> in model space - so the drawn
 * bird slid further into the ground every tick while the entity itself never moved. It surfaced as two
 * complaints that sound unrelated: the pigeons are sinking, and the pigeons have no hitbox. The hitbox
 * was fine; players were swinging at a bird that was no longer where it was drawn.
 *
 * <p><b>Why a unit test.</b> The bug is arithmetic, and arithmetic is testable. Everything downstream of
 * it is a renderer, which neither test layer in this repo can see - a GameTest would have watched a
 * pigeon sink and reported success. The property that was actually violated is that the value is
 * bounded, so that is what is asserted, over more ticks than any session will run.
 */
class PigeonFlapTest {

    private static final int A_LONG_TIME = 400_000;   // over five hours of ticks

    @Test
    @DisplayName("a flying pigeon's flap angle never leaves [0, 1], however long it lives")
    void flapAngleStaysBounded() {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (int tick = 0; tick < A_LONG_TIME; tick += 7) {
            float angle = PigeonEntity.flapAngle(tick, 0.5F, false, 3);
            min = Math.min(min, angle);
            max = Math.max(max, angle);
        }
        assertTrue(max <= 1.0F,
            "the flap angle reached " + max + ". The parrot model turns this into a downward offset on "
                + "every part, so an unbounded value walks the bird out of its own hitbox");
        assertTrue(min >= 0.0F, "the flap angle went negative at " + min);
        // And it genuinely oscillates rather than being pinned - a constant would pass the bounds above
        // and give a bird with rigid wings.
        assertTrue(max - min > 0.9F, "the wingbeat barely moves: range was " + (max - min));
    }

    @Test
    @DisplayName("a pigeon on the ground sits exactly on its feet")
    void groundedPigeonDoesNotBob() {
        for (int tick = 0; tick < 1000; tick++) {
            assertEquals(0.0F, PigeonEntity.flapAngle(tick, 0.0F, true, 11), 0.0F,
                "a standing pigeon must not be offset at all - any non-zero value here is a bird "
                    + "hovering above or sunk below the block it is standing on");
        }
    }

    @Test
    @DisplayName("two pigeons alive for the same time are not in lockstep")
    void differentBirdsBeatOutOfPhase() {
        int differences = 0;
        for (int tick = 0; tick < 200; tick++) {
            if (PigeonEntity.flapAngle(tick, 0.0F, false, 1)
                != PigeonEntity.flapAngle(tick, 0.0F, false, 2)) {
                differences++;
            }
        }
        assertTrue(differences > 190,
            "two pigeons flapped in unison on " + (200 - differences) + " of 200 ticks - the entity id "
                + "is meant to shift the phase, which is the whole reason it is a parameter");
    }
}
