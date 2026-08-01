package com.flatts.recompile.content.menu;

/**
 * The connected-storage panel's two pieces of arithmetic: how much a click withdraws, and which slice of
 * a long material list is on screen (issue #86).
 *
 * <p>Both were bugs a player hit in playtest. Left-clicking a row took 64 of whatever it was, so pulling
 * three Rebar meant walking a stack back; and the panel drew a fixed number of rows, printed "+6 more",
 * and offered no way to reach them - a barrel holds 27 stacks against roughly seven visible rows, so
 * "most of your storage is unreachable" was the normal state rather than an edge case.
 *
 * <p><b>Pure on purpose.</b> The screen is client-only and cannot be driven by a GameTest, and the menu
 * needs a world. Neither instrument can see this arithmetic, so it lives here as plain functions with
 * JUnit tests, per the repo's rule that pure logic gets a unit test rather than an in-world one.
 */
public final class ScrapPanelInteraction {

    /**
     * How much one click takes.
     *
     * <p>Vanilla's container convention is left-takes-stack, and this deliberately departs from it. The
     * panel is not a slot you are swapping with - it is a withdrawal list against remote storage, where
     * the common action is "give me a few" and the expensive mistake is walking 60 items back. Storage
     * mods converge on the same answer for the same reason.
     */
    public enum Mode {
        /** Left-click: one item. */
        ONE,
        /** Shift-left-click: as much as a stack holds. */
        STACK,
        /** Right-click: half a stack. */
        HALF
    }

    /**
     * Bits reserved for the item's registry id, with the mode packed above them.
     *
     * <p>The panel talks to the server through {@code clickMenuButton}, whose payload is a single int,
     * and that int already carries the item's registry id (rather than a row index, so a list one tick
     * stale cannot withdraw the wrong thing). The mode has to travel in the same int. 24 bits leaves room
     * for 16,777,215 items, which no modpack approaches - the largest known are five figures.
     */
    private static final int MODE_SHIFT = 24;
    private static final int ITEM_MASK = (1 << MODE_SHIFT) - 1;

    private static final Mode[] MODES = Mode.values();

    private ScrapPanelInteraction() {
    }

    /** Pack an item id and a click mode into one menu-button id. */
    public static int encode(int itemId, Mode mode) {
        if (itemId < 0 || itemId > ITEM_MASK) {
            throw new IllegalArgumentException("item id " + itemId + " does not fit in "
                + MODE_SHIFT + " bits - the panel's button encoding needs widening");
        }
        return itemId | (mode.ordinal() << MODE_SHIFT);
    }

    /** The item id out of a packed button. */
    public static int itemIdOf(int button) {
        return button & ITEM_MASK;
    }

    /**
     * The click mode out of a packed button, or {@code null} if the button is malformed.
     *
     * <p>Null rather than a default, because this value comes off the wire: a client that sends garbage
     * should get a no-op, not a silently-chosen behaviour on an item it named.
     */
    public static Mode modeOf(int button) {
        if (button < 0) {
            return null;
        }
        int ordinal = button >>> MODE_SHIFT;
        return ordinal < MODES.length ? MODES[ordinal] : null;
    }

    /**
     * How many items a click of this mode should pull, given what is there and what a stack holds.
     *
     * <p>Never returns more than is available and never returns zero for a non-empty source, so a HALF
     * click on a single item still yields that item rather than nothing.
     */
    public static int amountFor(Mode mode, int available, int stackMax) {
        if (available <= 0 || stackMax <= 0) {
            return 0;
        }
        int capped = Math.min(available, stackMax);
        return switch (mode) {
            case ONE -> 1;
            case STACK -> capped;
            case HALF -> Math.max(1, capped / 2);
        };
    }

    /**
     * Clamp a scroll offset so the visible window always sits inside the list.
     *
     * <p>Returns 0 when everything fits, which is what makes the scroll a no-op on a short list rather
     * than something that can drift the view off the top.
     */
    public static int clampScroll(int offset, int total, int visible) {
        int max = Math.max(0, total - visible);
        return Math.max(0, Math.min(offset, max));
    }
}
