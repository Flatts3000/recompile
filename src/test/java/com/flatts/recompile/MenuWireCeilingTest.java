package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.menu.WideSync;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every number a machine screen shows survives a 16-bit wire (#369).
 *
 * <p><b>A menu data slot is a short on the wire, whatever its Java type says.</b>
 * {@code ClientboundContainerSetDataPacket} keeps its value in an {@code int} field and then writes
 * it with {@code writeShort}, reading it back with {@code readShort}. Nothing logs the loss and the
 * server stays right, so the only symptom is a screen that lies.
 *
 * <p>{@link #wire} is what makes this a test of the REAL failure rather than of the encoding in
 * isolation: it applies exactly the narrowing the packet performs. A unit test rather than a GameTest
 * because the fix is pure arithmetic - no world, no server, no packet.
 *
 * <p>The values here are the CONFIG MAXIMA, because that is where the defect lives. No shipped default
 * comes near the ceiling, which is exactly why this went unnoticed for a release: it takes a pack
 * retune to expose, and then it looks like a broken machine rather than a broken screen.
 */
class MenuWireCeilingTest {

    /** Exactly what the packet does to a data slot value: write a short, read it back signed. */
    private static int wire(int value) {
        return (short) value;
    }

    private static int roundTrip(int value) {
        return WideSync.combine(wire(WideSync.low(value)), wire(WideSync.high(value)));
    }

    // ---- the ceilings a pack can actually configure -------------------------------------
    private static final int MAX_GROW_TICKS = 240_000;      // hydroponicsGrowTicks
    private static final int MAX_COOK_TICKS = 240_000;      // treeNurseryCookTicks
    private static final int MAX_TANK = 1_000_000;          // both tankCapacity settings
    private static final int MAX_FE_PER_TICK = 100_000;     // hydroponicsFePerTick

    @Test
    void a_proportion_always_fits_one_slot() {
        // The whole point of syncing a ratio: no configured goal can push it past a short.
        for (int goal : List.of(1, 400, 32_767, 32_768, MAX_GROW_TICKS)) {
            for (int progress : List.of(0, 1, goal / 2, goal)) {
                int permille = WideSync.permille(progress, goal);
                assertTrue(permille >= 0 && permille <= WideSync.PERMILLE,
                    "permille out of range for " + progress + "/" + goal + ": " + permille);
                assertEquals(permille, wire(permille),
                    "a proportion should survive the wire untouched: " + permille);
            }
        }
    }

    @Test
    void any_progress_at_all_reads_as_started() {
        // A bay whose goal a pack pushed to 240,000 ticks would otherwise report zero for its first
        // four minutes, and every screen here treats zero as idle - the Bay dims both gauges on it.
        assertEquals(0, WideSync.permille(0, MAX_GROW_TICKS), "nothing done is nothing shown");
        assertEquals(1, WideSync.permille(1, MAX_GROW_TICKS),
            "one tick into the longest configurable batch must not read as idle");
        assertEquals(WideSync.PERMILLE, WideSync.permille(MAX_GROW_TICKS, MAX_GROW_TICKS));
        assertEquals(WideSync.PERMILLE, WideSync.permille(MAX_GROW_TICKS + 5, MAX_GROW_TICKS),
            "overshoot is still full, never more");
    }

    @Test
    void a_proportion_never_divides_by_a_goal_of_zero() {
        assertEquals(0, WideSync.permille(5, 0));
        assertEquals(0, WideSync.permille(5, -1));
        assertEquals(0, WideSync.permille(-5, 100));
    }

    @Test
    void the_old_shape_really_was_broken() {
        // Regression anchor: this is the defect #369 reported, reproduced through the real narrowing.
        // The goal used to travel whole, so 240,000 arrived as -11,776; GuiPainter.arrow then does
        // Math.max(1, total), which turns that into 1, and min(progress, 1) fills the arrow completely
        // on the first tick of a machine that is working correctly.
        int wiredGoal = wire(MAX_GROW_TICKS);
        assertNotEquals(MAX_GROW_TICKS, wiredGoal, "if this ever passes, the wire stopped being 16 bits");
        assertTrue(wiredGoal < 0, "the reported symptom needs the goal to arrive negative, got " + wiredGoal);
        assertEquals(1, Math.max(1, wiredGoal), "which GuiPainter.arrow clamps to a goal of 1");

        // And the same value the new way round.
        assertEquals(WideSync.PERMILLE / 2, WideSync.permille(MAX_GROW_TICKS / 2, MAX_GROW_TICKS));
    }

    @Test
    void a_tank_survives_two_slots_at_its_configured_maximum() {
        // The gauges' tooltips print millibuckets, so these cannot be sent as a ratio.
        for (int amount : List.of(0, 1, 4_000, 32_767, 32_768, 65_535, 65_536, 999_999, MAX_TANK)) {
            assertEquals(amount, roundTrip(amount), "tank amount did not survive the wire: " + amount);
        }
    }

    @Test
    void a_battery_survives_two_slots_at_its_configured_maximum() {
        // Sized at one batch: growTicks x fePerTick, clamped to Integer.MAX_VALUE because that is both
        // what an int holds and what two slots can carry.
        long full = (long) MAX_GROW_TICKS * MAX_FE_PER_TICK;
        assertTrue(full > Integer.MAX_VALUE, "the clamp is only interesting if the product overflows");
        int clamped = (int) Math.min(full, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, clamped);
        assertEquals(clamped, roundTrip(clamped), "a clamped battery must still reach the screen intact");

        // The unclamped product is what used to be handed to the energy handler.
        assertTrue((int) full < 0, "the old int multiplication wrapped negative, which is the defect");
    }

    @Test
    void seconds_remaining_fit_a_single_slot() {
        // The nursery's countdown prints seconds, so seconds travel rather than ticks. Twenty ticks a
        // second against the 240,000-tick ceiling is 12,000, comfortably inside a short.
        int worst = (MAX_COOK_TICKS + 19) / 20;
        assertEquals(12_000, worst);
        assertTrue(worst <= Short.MAX_VALUE, "seconds must fit one slot, got " + worst);
        assertEquals(worst, wire(worst));
    }

    @Test
    void the_split_agrees_with_the_market_implementation() {
        // Market delegates to WideSync now. If the two ever disagree, one screen is wrong and the
        // other is not, which is the exact drift BalanceSync's javadoc warns about.
        for (int value : List.of(0, 1, 45, 32_768, 65_536, 1_000_000, 1_000_000_000)) {
            assertEquals(com.flatts.recompile.content.market.Market.syncLow(value), WideSync.low(value));
            assertEquals(com.flatts.recompile.content.market.Market.syncHigh(value), WideSync.high(value));
            assertEquals(value, com.flatts.recompile.content.market.Market.fromSync(
                wire(WideSync.low(value)), wire(WideSync.high(value))));
        }
    }
}
