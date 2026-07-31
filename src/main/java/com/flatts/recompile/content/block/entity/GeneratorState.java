package com.flatts.recompile.content.block.entity;

/**
 * Why a generator is or is not producing right now - the answer a player is actually asking when they
 * hover the meter.
 *
 * <p>Split out as a pure function of four numbers so it can be <b>unit tested</b> rather than only seen
 * in a screenshot. It exists because the first version of that tooltip conflated two different states
 * and told a player "out of fuel" while five slots sat full of rags: the generator was idle because its
 * buffer was full, which is the opposite problem and needs the opposite action.
 *
 * <p>"Idle" is not one state. A generator that has stopped because it is full is working correctly and
 * needs a consumer; one that has stopped because it is empty needs fuel. Saying the wrong one sends the
 * player to fix the wrong thing.
 */
public enum GeneratorState {

    /** Burning now. */
    GENERATING("generating"),
    /** Has fuel, but the buffer is full - deliberately not burning. Nothing is wrong. */
    BUFFER_FULL("buffer_full"),
    /** Nothing to burn. */
    OUT_OF_FUEL("out_of_fuel");

    private final String key;

    GeneratorState(String key) {
        this.key = key;
    }

    /** The lang key for this state's tooltip line. */
    public String translationKey() {
        return "tooltip.recompile.generator_" + key;
    }

    /**
     * Read the state from what the machine actually exposes.
     *
     * <p>Order matters. <b>Burning wins over full</b>: a generator finishing the item it already lit is
     * still generating once the buffer tops out, and calling that "buffer full" would flicker the
     * tooltip every time it filled mid-burn.
     *
     * <p>There is no {@code hasFuel} parameter, and that is deliberate rather than an omission. The
     * generator lights fuel on any tick where it is not burning and has room, so <b>"not burning and not
     * full" already implies there is nothing to burn</b> - taking a separate flag would let the display
     * contradict the machine instead of being derived from it. The one-tick window between fuel arriving
     * and lighting reads as out-of-fuel, which is true when the frame is drawn.
     */
    public static GeneratorState of(int stored, int capacity, boolean burning) {
        if (burning) {
            return GENERATING;
        }
        if (capacity > 0 && stored >= capacity) {
            return BUFFER_FULL;
        }
        return OUT_OF_FUEL;
    }
}
