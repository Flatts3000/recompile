package com.flatts.recompile.client.gui;

import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.Rect;
import com.flatts.recompile.gui.ScreenLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The client-side visitor over a {@link ScreenLayout}: everything a screen draws, addressed by name.
 *
 * <p><b>A screen never computes a coordinate.</b> It asks for "the water gauge" or "row 3" and the
 * painter resolves that against the same declaration the menu placed its slots from, adding the panel
 * origin the screen only learns at {@code init} time. That is what makes it impossible for a slot and
 * the chrome under it to disagree, which is the defect issue #164 exists to close.
 *
 * <p>Everything here takes a group name because the alternative - handing back a mutable graphics object
 * and a pair of offsets - is the escape hatch that would let a screen quietly go back to typing numbers.
 * If a screen needs something this cannot express, that is a missing verb rather than a licence to reach
 * around it.
 */
public final class GuiPainter {

    private final GuiGraphicsExtractor graphics;
    private final ScreenLayout layout;
    private final Font font;
    private final int left;
    private final int top;

    GuiPainter(GuiGraphicsExtractor graphics, ScreenLayout layout, Font font, int left, int top) {
        this.graphics = graphics;
        this.layout = layout;
        this.font = font;
        this.left = left;
        this.top = top;
    }

    public ScreenLayout layout() {
        return layout;
    }

    public Font font() {
        return font;
    }

    /** How many cells a group declared - the count a list screen paginates against. */
    public int count(String name) {
        return layout.group(name).count();
    }

    /** A group's rectangle in absolute screen coordinates. */
    public Rect at(String name) {
        return layout.rect(name).offset(left, top);
    }

    public Rect at(String name, int index) {
        return layout.rect(name, index).offset(left, top);
    }

    // ---------------- hit testing ----------------
    //
    // Delegated to the layout, which owns geometry. Writing the loop out here as well is exactly how
    // this framework would grow a second copy of the truth it exists to keep single - and it did, until
    // review caught the same code sitting in both this class and LayoutScreen.

    public boolean isOver(String name, double mouseX, double mouseY) {
        return layout.contains(name, left, top, mouseX, mouseY);
    }

    public boolean isOver(String name, int index, double mouseX, double mouseY) {
        return at(name, index).contains(mouseX, mouseY);
    }

    /** Which cell of a group the mouse is over, or {@code -1}. */
    public int overIndex(String name, int limit, double mouseX, double mouseY) {
        return layout.indexAt(name, limit, left, top, mouseX, mouseY);
    }

    // ---------------- chrome ----------------

    /**
     * Draw everything static the layout declares, in declaration order.
     *
     * <p>Run before a screen paints anything, and the reason a screen has no slot-drawing loop of its
     * own. Three screens used to carry that loop by hand, each with its own idea of what a slot looks
     * like; the Hydroponics Bay drew vanilla's sprite while the Burner Generator and the Tree Nursery
     * filled five rectangles that approximated it, so the same mod shipped two different-looking panels.
     */
    void drawChrome() {
        for (ScreenLayout.Group group : layout.groups()) {
            if (!group.hasChrome()) {
                continue;
            }
            switch (group.kind()) {
                case PANEL -> VanillaGui.panel(graphics, left, top, layout.width(), layout.height());
                case SLOT, CELL -> {
                    for (int i = 0; i < group.count(); i++) {
                        Rect rect = group.cell(i).offset(left, top);
                        VanillaGui.slot(graphics, rect.x(), rect.y());
                    }
                }
                case WELL -> {
                    Rect rect = group.only().offset(left, top);
                    VanillaGui.well(graphics, rect.x(), rect.y(), rect.width(), rect.height());
                }
                // An arrow's fill is dynamic, a region is by definition undrawn, and a backdrop is an
                // image or a colour only the screen knows. All three belong to the screen's own pass.
                case ARROW, REGION, BACKDROP -> { }
            }
        }
    }

    /** A whole vanilla container background reused as-is, for a screen built on one. */
    public void background(String name, Identifier texture) {
        Rect rect = at(name);
        VanillaGui.vanillaBackground(graphics, texture, rect.x(), rect.y(),
            rect.width(), rect.height());
    }

    /** A flat slab for a surface that is deliberately not vanilla chrome. */
    public void slab(String name, int body) {
        Rect rect = at(name);
        VanillaGui.slab(graphics, rect.x(), rect.y(), rect.width(), rect.height(), body);
    }

    // ---------------- widgets ----------------

    /**
     * Fill a well from the bottom.
     *
     * <p>Clamped, because a config change can leave stored energy above the capacity the gauge is scaled
     * to, and a bar drawn past its own frame looks like a rendering fault rather than a full one.
     *
     * <p>Always call it, empty or not: a bar that only appears once it has something in it makes "this
     * machine has no water" and "this screen has no water gauge" look identical, which is the confusion
     * gauges exist to remove. The well is drawn by the chrome pass, so an empty gauge is still a gauge.
     */
    public void gauge(String name, int amount, int capacity, int colour) {
        Rect rect = at(name);
        if (capacity <= 0 || amount <= 0) {
            return;
        }
        int inner = rect.height() - 2;
        int fill = (int) Math.min(inner, (long) inner * amount / capacity);
        if (fill > 0) {
            graphics.fill(rect.x() + 1, rect.bottom() - 1 - fill,
                rect.right() - 1, rect.bottom() - 1, colour);
        }
    }

    /** Vanilla's furnace flame, burning down by proportion the way every furnace in the game does. */
    public void flame(String name, int remaining, int total) {
        Rect rect = at(name);
        int goal = Math.max(1, total);
        int left = Math.max(0, Math.min(remaining, goal));
        VanillaGui.flame(graphics, rect.x(), rect.y(),
            (left * GuiTheme.FLAME_H + goal - 1) / goal);
    }

    /** Vanilla's progress arrow, filled by proportion. Rounds up exactly the way vanilla's does. */
    public void arrow(String name, int progress, int total) {
        Rect rect = at(name);
        int goal = Math.max(1, total);
        int done = Math.max(0, Math.min(progress, goal));
        VanillaGui.progressArrow(graphics, rect.x(), rect.y(),
            (done * GuiTheme.ARROW_W + goal - 1) / goal);
    }

    /** A wash over a cell - a hover, or the tint on a picker's chosen entry. */
    public void tint(String name, int index, int colour) {
        Rect rect = at(name, index);
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), colour);
    }

    /** A wash over a cell grown by {@code pad} on every side, for a row whose hover reads wider than it. */
    public void tintPadded(String name, int index, int pad, int colour) {
        Rect rect = at(name, index);
        graphics.fill(rect.x() - pad, rect.y() - pad, rect.right() + pad, rect.bottom() + pad, colour);
    }

    /** A ring drawn just outside a cell, for the picker's selection. */
    public void ring(String name, int index, int pad, int thickness, int colour) {
        Rect rect = at(name, index);
        VanillaGui.border(graphics, rect.x() - pad, rect.y() - pad,
            rect.width() + pad * 2, rect.height() + pad * 2, thickness, colour);
    }

    /** An item drawn at a cell's origin. */
    public void item(String name, int index, ItemStack stack) {
        Rect rect = at(name, index);
        graphics.item(stack, rect.x(), rect.y());
    }

    // ---------------- text ----------------

    /** One line at a region's origin. */
    public void text(String name, String value, int colour) {
        Rect rect = at(name);
        graphics.text(font, value, rect.x(), rect.y(), colour, false);
    }

    /** One line inside a cell, offset from its origin - a count beside a row's icon. */
    public void textIn(String name, int index, int dx, int dy, String value, int colour) {
        Rect rect = at(name, index);
        graphics.text(font, value, rect.x() + dx, rect.y() + dy, colour, false);
    }

    /**
     * Prose wrapped to the region's own width.
     *
     * <p>Wrapping is the default rather than an option because every string on a screen is either written
     * by hand or comes from a lang file, so none can be assumed short. "No storage connected" is nearly
     * twice the usable width of the panel it lives in and used to be drawn straight through the panel's
     * right edge and across the world behind it; a translation into a longer language would do the same
     * to any of the others.
     */
    public void wrapped(String name, Component text, int colour) {
        wrapped(name, 0, text, colour);
    }

    /** The same, at a cell of a multi-row group - a list's tail line under however many rows it drew. */
    public void wrapped(String name, int index, Component text, int colour) {
        Rect rect = at(name, index);
        int line = rect.y();
        for (FormattedCharSequence part : font.split(text, rect.width())) {
            graphics.text(font, part, rect.x(), line, colour, false);
            line += font.lineHeight;
        }
    }
}
