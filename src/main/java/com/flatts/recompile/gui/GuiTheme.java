package com.flatts.recompile.gui;

/**
 * Every colour and every metric the mod's screens draw with, in one place.
 *
 * <p><b>This is the home a colour has.</b> The rule and the defect behind it are recorded on
 * {@code GuiColourConsistencyTest}: the same seven values were declared in two and three files each and
 * agreed only because they were copy-pasted on one afternoon. The constants started out on
 * {@code VanillaGui}, which is a client class; they moved here when the framework landed, because a
 * <b>layout</b> has to name a colour and a layout is common code that may not load client classes.
 *
 * <p>Named for MEANING rather than appearance, so "what colour is power" has exactly one answer and
 * changing it changes every gauge at once.
 *
 * <p>The metrics half is vanilla's own grammar - the numbers every modder re-derives. They have not
 * changed since Beta and there is no reason for a fifth copy of them to exist in a fifth file.
 */
public final class GuiTheme {

    private GuiTheme() {
    }

    // ---------------- chrome ----------------

    /** The panel body, for the rare case something must be filled rather than nine-sliced. */
    public static final int PANEL_BODY = 0xFFC6C6C6;
    /** The two bevel tones: vanilla's lit edge and its shaded edge. */
    public static final int BEVEL_LIGHT = 0xFFFFFFFF;
    public static final int BEVEL_DARK = 0xFF555555;
    /** The near-black outline vanilla draws around a recessed area. */
    public static final int OUTLINE_DARK = 0xFF202020;

    /** Vanilla's slot palette, for the recessed wells that gauges live in. */
    public static final int SLOT_FACE = 0xFF8B8B8B;
    public static final int SLOT_SHADOW = 0xFF373737;
    public static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;

    /** The body of a side panel that is deliberately NOT vanilla chrome - the connected-storage shelf. */
    public static final int SIDE_PANEL_BODY = 0xFF3A3A3A;

    // ---------------- resources ----------------

    /** A power bar with charge in it, and the same bar empty. */
    public static final int POWER = 0xFFE02B2B;
    public static final int POWER_IDLE = 0xFF8A1F1F;

    /**
     * A water gauge with water in it, and the same gauge empty.
     *
     * <p>The Tree Nursery drew its tank in {@code 0xFF3A78C2} and the Hydroponics Bay drew its tank in
     * this - two blues for one substance, in a mod where both machines are on the same water economy.
     * Neither file could see the other, so nothing was wrong in either one. The nursery now reads this.
     */
    public static final int WATER = 0xFF3F76E4;
    public static final int WATER_IDLE = 0xFF2A4E96;

    // ---------------- text ----------------

    /** Vanilla's label tone - the colour every container screen draws its title in. */
    public static final int TEXT_LABEL = 0xFF404040;
    /** Panel prose on a dark ground: a heading, a muted note, and the dimmer summary between them. */
    public static final int TEXT_BRIGHT = 0xFFD0D0D0;
    public static final int TEXT_DIM = 0xFF9AA0A6;
    public static final int TEXT_MUTED = 0xFF808080;
    /** Something the player can act on, and something that has gone right. */
    public static final int TEXT_WARN = 0xFFD05050;
    public static final int TEXT_GOOD = 0xFF7FD07F;

    // ---------------- selection ----------------

    /** The border around a chosen entry in a picker, and the wash over the entry itself. */
    public static final int SELECT = 0xFF7CFC00;
    public static final int SELECT_TINT = 0x604CAF50;
    /**
     * Hover, at two strengths. A picker cell is 16px and wants a wash strong enough to read at that
     * size; a full-width list row is twenty times the area and the same alpha over it glares.
     */
    public static final int HOVER_CELL = 0x80FFFFFF;
    public static final int HOVER_ROW = 0x40FFFFFF;

    // ---------------- vanilla's container metrics ----------------

    /** The width every vanilla container panel has had since Beta. */
    public static final int PANEL_W = 176;
    /** The height of a container with one 3-row inventory and nothing above it. */
    public static final int PANEL_H = 166;
    /** Centre-to-centre distance between slots, and the drawn size of a slot's chrome. */
    public static final int SLOT_PITCH = 18;
    /** A slot's contents are 16x16; its bevel is drawn one pixel outside that. */
    public static final int SLOT_SIZE = 16;
    /** Where the player's grid starts horizontally, and where the title sits. */
    public static final int INVENTORY_X = 8;
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 6;

    /**
     * The gap between the bottom row of the backpack and the hotbar, and between the grid and its label.
     *
     * <p>Vanilla's grammar rather than a choice: every container in the game puts the hotbar four pixels
     * below the backpack and the "Inventory" label twelve above it. Declaring them once is what lets
     * {@link ScreenLayout.Builder#playerInventory(int)} take a single number.
     */
    public static final int HOTBAR_GAP = 4;
    public static final int LABEL_RISE = 12;

    /**
     * The width the overlap sweep reserves for a text label.
     *
     * <p>A bound, not a measurement - the real width depends on the font and on which language the player
     * is running, and neither exists on a server. Wide enough that anything clearing it clears any
     * plausible translation of "Inventory".
     */
    public static final int LABEL_W = 80;

    /** Vanilla's progress arrow, whose size is fixed by the sprite rather than chosen. */
    /** Vanilla's furnace flame, which is square. Both halves live at (56,36) in furnace.png. */
    public static final int FLAME_W = 14;
    public static final int FLAME_H = 14;

    public static final int ARROW_W = 24;
    public static final int ARROW_H = 17;
}
