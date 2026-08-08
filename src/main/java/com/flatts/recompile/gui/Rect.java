package com.flatts.recompile.gui;

/**
 * A rectangle in screen space, in the coordinate system every part of this framework uses.
 *
 * <p><b>Coordinates are panel-relative until a {@code GuiPainter} makes them absolute.</b> A layout is
 * built before the screen object exists - {@code imageWidth} and {@code imageHeight} are final and pass
 * through {@code super(...)}, so nothing can know {@code leftPos}/{@code topPos} at declaration time.
 * Every rect here is therefore an offset from the panel's top-left, which is also exactly what
 * {@code Slot.x}/{@code Slot.y} want.
 *
 * <p>Common code on purpose: a menu is constructed on a dedicated server, so this may not import
 * anything under {@code net.minecraft.client}. It imports nothing at all.
 */
public record Rect(int x, int y, int width, int height) {

    /** Whether a point is inside, using the half-open convention every vanilla hit test uses. */
    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /** Whether two rects share any pixel. The test {@code MenuLayoutTests} runs over every element. */
    public boolean overlaps(Rect other) {
        return x < other.x + other.width && other.x < x + width
            && y < other.y + other.height && other.y < y + height;
    }

    /** This rect moved by a panel origin, for the moment a client actually draws it. */
    public Rect offset(int dx, int dy) {
        return new Rect(x + dx, y + dy, width, height);
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }
}
