package com.flatts.recompile.content.menu;

/**
 * Getting a number bigger than a short onto a screen.
 *
 * <p><b>A menu data slot is 16 bits on the wire, whatever its Java type says.</b>
 * {@code ClientboundContainerSetDataPacket} keeps its value in an {@code int} field and then writes
 * it with {@code writeShort}, reading it back with {@code readShort}:
 *
 * <pre>{@code
 * this.id = input.readShort();
 * this.value = input.readShort();
 * }</pre>
 *
 * <p>So a synced value over 32,767 reaches the screen wrapped negative and one over 65,535 arrives
 * truncated. <b>Nothing logs it and the server stays right</b>, so the only symptom is a screen
 * quoting a number the player knows is wrong, or a gauge scaled against a number nobody agreed to.
 *
 * <p><b>The mod believed for a release that one value could reach the ceiling</b> - the market's scrip
 * balance, which is why {@code Market.syncLow} exists and why {@code CLAUDE.md} said
 * {@code hydroponicsGrowTicks} was the only other candidate. Both were wrong. **Eight** config values
 * have a maximum above 32,767, and five of them feed a data slot: the Hydroponics Bay's grow ticks,
 * tank and battery, and the Tree Nursery's cook ticks and tank. #369 reported one arrow; the same
 * defect was live on the nursery's arrow and on all four gauges, unreported, because no shipped
 * default reaches the ceiling and only a pack retune exposes it.
 *
 * <p><b>The rule that came out of it: sync what the screen DISPLAYS, not the quantity behind it.</b>
 * An arrow shows a proportion, so {@link #permille} sends a proportion and one slot always suffices.
 * A tooltip that prints millibuckets needs the millibuckets, so those travel as two halves. A tooltip
 * that prints seconds needs seconds, which fit. Applying that rule made the Bay's progress cost one
 * slot instead of two, and it is why this class has both a splitter and a ratio rather than only the
 * splitter.
 *
 * <p>The arithmetic is pure and lives here alone, because two copies of a bit shift is exactly the
 * kind of thing that drifts in one screen and not the other. {@code Market} delegates to it rather
 * than keeping the second copy it used to have.
 */
public final class WideSync {

    /** The largest value {@link #permille} will ever report, and the total an arrow scales against. */
    public static final int PERMILLE = 1000;

    private WideSync() {
    }

    /** The low 16 bits. Put this in the LOWER-numbered slot by convention, though nothing depends on it. */
    public static int low(int value) {
        return value & 0xFFFF;
    }

    /** The high 16 bits. */
    public static int high(int value) {
        return (value >>> 16) & 0xFFFF;
    }

    /**
     * The two halves put back together.
     *
     * <p>Both are masked because {@code readShort} sign-extends: a low half of 59,392 arrives as
     * -6,144, and adding that to a shifted high half would come out short by 65,536.
     */
    public static int combine(int low, int high) {
        return ((high & 0xFFFF) << 16) | (low & 0xFFFF);
    }

    /**
     * A proportion in thousandths, which always fits a slot however large the two inputs are.
     *
     * <p><b>Any progress at all reports at least 1.</b> A bay whose goal a pack has pushed to 240,000
     * ticks would otherwise spend its first four minutes reporting zero, and every screen in this mod
     * treats "progress is zero" as idle - the Bay dims both its gauges on it. Reading as barely started
     * is right; reading as not started is a machine that looks broken for four minutes.
     *
     * @param progress how far along, in whatever unit; negative is treated as none
     * @param goal     the target in the same unit; zero or less reports nothing rather than dividing
     */
    public static int permille(int progress, int goal) {
        if (progress <= 0 || goal <= 0) {
            return 0;
        }
        if (progress >= goal) {
            return PERMILLE;
        }
        return Math.max(1, (int) ((long) progress * PERMILLE / goal));
    }
}
