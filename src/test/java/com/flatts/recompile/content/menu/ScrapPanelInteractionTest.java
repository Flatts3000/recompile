package com.flatts.recompile.content.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.menu.ScrapPanelInteraction.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The connected-storage panel's arithmetic (issue #86), which nothing else can reach: the screen is
 * client-only and the menu needs a world, so a GameTest cannot see either function.
 */
class ScrapPanelInteractionTest {

    @ParameterizedTest
    @EnumSource(Mode.class)
    @DisplayName("a packed button round-trips both halves, for every mode")
    void packingRoundTrips(Mode mode) {
        for (int itemId : new int[] {0, 1, 42, 1023, 65_535, 16_777_215}) {
            int button = ScrapPanelInteraction.encode(itemId, mode);
            assertEquals(itemId, ScrapPanelInteraction.itemIdOf(button),
                "item id lost for " + mode + " at " + itemId);
            assertEquals(mode, ScrapPanelInteraction.modeOf(button),
                "mode lost for " + mode + " at " + itemId);
        }
    }

    @Test
    @DisplayName("a packed button never collides with DEPOSIT_BUTTON")
    void neverCollidesWithDeposit() {
        // DEPOSIT_BUTTON is -1 and the whole scheme rests on packed ids staying non-negative. If a mode
        // were ever added at ordinal 128 the shift would reach the sign bit and a withdraw would read as
        // a deposit of the cursor - silent, and destructive to whatever was being held.
        for (Mode mode : Mode.values()) {
            for (int itemId : new int[] {0, 16_777_215}) {
                int button = ScrapPanelInteraction.encode(itemId, mode);
                assertTrue(button >= 0, mode + " packed to a negative button: " + button);
                assertNotEquals(ScrapCraftingStationMenu.DEPOSIT_BUTTON, button);
            }
        }
    }

    @Test
    @DisplayName("an item id too wide to encode fails loudly rather than corrupting the mode")
    void oversizedItemIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> ScrapPanelInteraction.encode(16_777_216, Mode.ONE));
        assertThrows(IllegalArgumentException.class,
            () -> ScrapPanelInteraction.encode(-1, Mode.ONE));
    }

    @Test
    @DisplayName("a malformed button yields no mode, so a bad client gets a no-op")
    void malformedButtonHasNoMode() {
        assertNull(ScrapPanelInteraction.modeOf(-1), "the deposit button is not a withdraw");
        assertNull(ScrapPanelInteraction.modeOf(Integer.MIN_VALUE));
        // An ordinal past the last mode: reachable by any client that makes one up.
        assertNull(ScrapPanelInteraction.modeOf(99 << 24));
    }

    @Test
    @DisplayName("the click quantities are one, a stack, and half")
    void quantities() {
        assertEquals(1, ScrapPanelInteraction.amountFor(Mode.ONE, 64, 64));
        assertEquals(64, ScrapPanelInteraction.amountFor(Mode.STACK, 999, 64));
        assertEquals(32, ScrapPanelInteraction.amountFor(Mode.HALF, 999, 64));
    }

    @Test
    @DisplayName("no mode ever pulls more than is there, or more than a stack holds")
    void neverOverdraws() {
        for (Mode mode : Mode.values()) {
            for (int available : new int[] {1, 2, 3, 7, 63, 64, 65, 1000}) {
                for (int stackMax : new int[] {1, 16, 64}) {
                    int amount = ScrapPanelInteraction.amountFor(mode, available, stackMax);
                    assertTrue(amount <= available,
                        mode + " pulled " + amount + " from " + available);
                    assertTrue(amount <= stackMax,
                        mode + " pulled " + amount + " past a stack of " + stackMax);
                    assertTrue(amount >= 1, mode + " pulled nothing from " + available);
                }
            }
        }
    }

    @Test
    @DisplayName("nothing available means nothing pulled, for every mode")
    void emptySourcePullsNothing() {
        for (Mode mode : Mode.values()) {
            assertEquals(0, ScrapPanelInteraction.amountFor(mode, 0, 64));
        }
    }

    @Test
    @DisplayName("half of one item is still one item, not none")
    void halfOfOneIsOne() {
        // The rounding case that would otherwise make right-click silently do nothing on the last item.
        assertEquals(1, ScrapPanelInteraction.amountFor(Mode.HALF, 1, 64));
        assertEquals(1, ScrapPanelInteraction.amountFor(Mode.HALF, 2, 64));
        assertEquals(1, ScrapPanelInteraction.amountFor(Mode.HALF, 3, 64));
    }

    @Test
    @DisplayName("a list that fits never scrolls")
    void shortListDoesNotScroll() {
        assertEquals(0, ScrapPanelInteraction.clampScroll(0, 3, 7));
        assertEquals(0, ScrapPanelInteraction.clampScroll(5, 3, 7), "scrolling a short list is a no-op");
        assertEquals(0, ScrapPanelInteraction.clampScroll(5, 7, 7), "exactly filling is not scrollable");
    }

    @Test
    @DisplayName("the last row is reachable and the window never runs off the end")
    void longListStopsAtTheLastRow() {
        // The reported bug: a barrel of 27 stacks against 7 visible rows. Every one must be reachable.
        assertEquals(20, ScrapPanelInteraction.clampScroll(99, 27, 7));
        assertEquals(20, ScrapPanelInteraction.clampScroll(20, 27, 7));
        assertEquals(19, ScrapPanelInteraction.clampScroll(19, 27, 7));
        assertEquals(0, ScrapPanelInteraction.clampScroll(-4, 27, 7), "cannot scroll above the top");
    }

    @Test
    @DisplayName("the window plus the offset always covers the list end exactly")
    void windowAlwaysReachesTheEnd() {
        for (int total = 0; total <= 40; total++) {
            for (int visible = 1; visible <= 10; visible++) {
                int max = ScrapPanelInteraction.clampScroll(Integer.MAX_VALUE, total, visible);
                assertTrue(max + visible >= total,
                    "scrolled fully to " + max + " with " + visible + " rows still hides part of "
                        + total + " - items would be unreachable, which is the bug this closes");
                assertTrue(max <= Math.max(0, total - 1) || total == 0,
                    "scrolled past the last row of " + total);
            }
        }
    }
}
