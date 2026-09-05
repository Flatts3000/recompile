package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.block.entity.HaulerDepotBlockEntity;
import com.flatts.recompile.content.item.ScrapHaulerItem;
import org.junit.jupiter.api.Test;

/**
 * The Hauler Depot's screen syncs two capacities through menu data slots, and a data slot is 16 bits
 * on the wire (see {@code BalanceSync}). Both sit under the ceiling by DESIGN rather than by accident,
 * which is what this pins: raise either past a short and the gauge wraps silently, with nothing logged
 * and the server still right. The spec (section 8) names #369 as the bug this prevents.
 */
class ScrapHaulerSyncTest {

    private static final int WIRE_CEILING = Short.MAX_VALUE;

    @Test
    void theDepotBufferFitsOneDataSlot() {
        assertTrue(HaulerDepotBlockEntity.CAPACITY <= WIRE_CEILING,
            "HaulerDepotBlockEntity.CAPACITY is " + HaulerDepotBlockEntity.CAPACITY
                + ", past the 16-bit menu data slot ceiling; split it as BalanceSync does");
    }

    @Test
    void theHaulerChargeFitsOneDataSlot() {
        assertTrue(ScrapHaulerItem.CAPACITY <= WIRE_CEILING,
            "ScrapHaulerItem.CAPACITY is " + ScrapHaulerItem.CAPACITY
                + ", past the 16-bit menu data slot ceiling; split it as BalanceSync does");
    }

    @Test
    void theWireNarrowingIsWhatWeThinkItIs() {
        // The packet writes a short. A value one past the ceiling comes back negative.
        int wrapped = (short) (WIRE_CEILING + 1);
        assertTrue(wrapped < 0, "expected the short narrowing to wrap negative, got " + wrapped);
    }
}
