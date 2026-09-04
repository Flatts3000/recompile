package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.market.Market;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A scrip balance survives the trip to a screen.
 *
 * <p><b>A menu data slot is 16 bits on the wire, whatever its Java type says.</b>
 * {@code ClientboundContainerSetDataPacket} keeps its value in an {@code int} field and then writes
 * it with {@code writeShort}, reading it back with {@code readShort} - so a balance over 32,767
 * arrives wrapped negative and one over 65,535 arrives truncated. Nothing logs it, the server stays
 * right, and the only symptom is a screen quoting a number the player knows is wrong.
 *
 * <p>Every other synced number in this mod is a tank or a buffer capped at 20,000 or less, so
 * nothing here had ever met the ceiling; a balance runs to {@link Market#MAX_BALANCE}. A unit test
 * because the fix is pure arithmetic - no world, no server, no packet - and {@link #wire} is what
 * makes it a test of the REAL failure rather than of the encoding in isolation.
 */
class MarketBalanceSyncTest {

    /** Exactly what the packet does to a data slot value: write a short, read it back signed. */
    private static int wire(int value) {
        return (short) value;
    }

    private static int roundTrip(int balance) {
        return Market.fromSync(wire(Market.syncLow(balance)), wire(Market.syncHigh(balance)));
    }

    @Test
    void a_balance_survives_the_short_sized_wire() {
        List<Integer> cases = List.of(
            0, 1, 45, 180, 1_500,
            32_767,                 // the last value a single slot could have carried
            32_768,                 // the first it could not: this one used to arrive as -32,768
            65_535, 65_536, 65_537, // either side of the low half rolling over
            999_999, 1_000_000,
            Market.MAX_BALANCE);

        for (int balance : cases) {
            assertEquals(balance, roundTrip(balance),
                "a balance of " + balance + " did not survive the data slot round trip");
        }
    }

    @Test
    void the_halves_are_actually_short_sized() {
        // If either half needed more than 16 bits the split would be pointless, and the failure
        // would look exactly like the bug it replaced.
        for (int balance : List.of(0, 32_768, 1_000_000, Market.MAX_BALANCE)) {
            assertTrue(Market.syncLow(balance) >= 0 && Market.syncLow(balance) <= 0xFFFF,
                "low half of " + balance + " is " + Market.syncLow(balance));
            assertTrue(Market.syncHigh(balance) >= 0 && Market.syncHigh(balance) <= 0xFFFF,
                "high half of " + balance + " is " + Market.syncHigh(balance));
        }
    }

    @Test
    void the_cap_is_what_the_split_can_carry() {
        // Two 16-bit halves reach 2^32; MAX_BALANCE has to sit inside that AND inside a positive
        // int, or fromSync would hand back a negative balance and every price would read affordable.
        assertTrue(Market.MAX_BALANCE > 0 && Market.MAX_BALANCE <= Integer.MAX_VALUE);
        assertTrue(roundTrip(Market.MAX_BALANCE) > 0,
            "the cap itself comes back negative, so a rich player would be handed everything free");
    }
}
