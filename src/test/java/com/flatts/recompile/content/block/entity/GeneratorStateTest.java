package com.flatts.recompile.content.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The mod's first JUnit tests. {@code unitTest} was enabled in {@code build.gradle} from the start and
 * {@code ./gradlew test} did nothing, because nothing here was a pure function worth calling directly.
 *
 * <p>{@link GeneratorState} is, and it earned the layer the hard way: the tooltip it replaces told a
 * player "Not generating - out of fuel" while five slots sat full of rags. The generator was idle
 * because its buffer was full - the opposite problem, needing the opposite action. That is a logic bug
 * with no world, no rendering and no server in it, so a GameTest was never the right instrument; it was
 * only ever going to be caught by a screenshot, and it was.
 */
class GeneratorStateTest {

    private static final int CAPACITY = 20_000;

    @Test
    @DisplayName("burning reads as generating")
    void burningGenerates() {
        assertEquals(GeneratorState.GENERATING, GeneratorState.of(0, CAPACITY, true));
        assertEquals(GeneratorState.GENERATING, GeneratorState.of(5_000, CAPACITY, true));
    }

    @Test
    @DisplayName("burning wins over a full buffer, so the tooltip does not flicker mid-burn")
    void burningWinsOverFull() {
        assertEquals(GeneratorState.GENERATING, GeneratorState.of(CAPACITY, CAPACITY, true));
    }

    @Test
    @DisplayName("the exact bug: full buffer with fuel is not out of fuel")
    void fullBufferIsNotOutOfFuel() {
        GeneratorState state = GeneratorState.of(CAPACITY, CAPACITY, false);
        assertEquals(GeneratorState.BUFFER_FULL, state,
            "a generator idle because it is full must not tell the player to add fuel");
    }

    @Test
    @DisplayName("over-full reads as full, not as something else")
    void overCapacityIsFull() {
        assertEquals(GeneratorState.BUFFER_FULL, GeneratorState.of(CAPACITY + 500, CAPACITY, false));
    }

    @Test
    @DisplayName("idle with room means nothing to burn")
    void idleWithRoomIsOutOfFuel() {
        assertEquals(GeneratorState.OUT_OF_FUEL, GeneratorState.of(0, CAPACITY, false));
        assertEquals(GeneratorState.OUT_OF_FUEL, GeneratorState.of(CAPACITY - 1, CAPACITY, false));
    }

    @Test
    @DisplayName("a zero-capacity generator does not report as full")
    void zeroCapacityIsNotFull() {
        // Guards the >= comparison: 0 >= 0 is true, so without the capacity > 0 check an
        // uninitialised or misconfigured generator would claim its buffer was full.
        assertEquals(GeneratorState.OUT_OF_FUEL, GeneratorState.of(0, 0, false));
    }

    @Test
    @DisplayName("every state has its own lang key")
    void statesHaveDistinctKeys() {
        long distinct = java.util.Arrays.stream(GeneratorState.values())
            .map(GeneratorState::translationKey)
            .distinct()
            .count();
        assertEquals(GeneratorState.values().length, distinct,
            "two states sharing a key would show the same message for different problems");
    }
}
