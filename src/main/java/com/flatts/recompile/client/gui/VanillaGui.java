package com.flatts.recompile.client.gui;

import com.flatts.recompile.gui.GuiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Vanilla GUI chrome, drawn from vanilla's own assets. The framework's chrome layer.
 *
 * <p><b>Why this exists.</b> The mod's screens were hand-filling flat rectangles in approximately
 * vanilla colours, and approximately is visible: the first Hydroponics Bay screen drew every slot as a
 * uniform dark ring with no bevel, so the panel read as a grey box with holes in it rather than as
 * something Minecraft drew. The fix is not better constants - it is to stop guessing and use the real
 * thing.
 *
 * <p><b>The panel is nine-sliced out of vanilla's furnace background</b> rather than filled. That texture
 * carries the mitred corners, the 1px black outline, the two-pixel white top-left bevel and the
 * two-pixel dark bottom-right bevel, none of which survive being re-derived by hand. Slicing it also
 * means a resource pack that restyles vanilla containers restyles ours - the panel is genuinely borrowed,
 * not imitated. The nine-slice samples only the border strips and one clean interior patch, so none of
 * the furnace's own slots or its arrow are ever inside the sampled regions.
 *
 * <p><b>Slots are the literal vanilla sprite</b> ({@code minecraft:container/slot}), the same one
 * {@code AbstractContainerScreen} draws its highlight over.
 *
 * <p><b>This is the only class in the mod allowed to know a render pipeline or an atlas dimension.</b>
 * Three screens used to carry a private {@code panel()} / {@code slot()} / {@code recess()} of their own -
 * flat fills that were not this - so the Burner Generator and the Tree Nursery drew a visibly different
 * panel from the Hydroponics Bay. {@code GuiFrameworkDisciplineTest} now holds that line.
 */
public final class VanillaGui {

    /** The panel source. Any vanilla container texture would do; the furnace is the plainest. */
    private static final Identifier PANEL =
        Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");

    private static final int TEX = 256;
    /** The furnace panel's own size inside its 256x256 sheet. */
    private static final int SRC_W = 176;
    private static final int SRC_H = 166;
    /** Border thickness sampled for the nine-slice: 1px outline plus the 2px bevel, plus one to spare. */
    private static final int EDGE = 4;
    /** A clean patch of panel body, chosen well clear of the furnace's slots and arrow. */
    private static final int BODY_U = 8;
    private static final int BODY_V = 5;
    private static final int BODY_SIZE = 8;

    /** Vanilla's progress arrow: the empty outline lives in the panel, the fill is a sprite. */
    private static final Identifier ARROW_FILL =
        Identifier.withDefaultNamespace("container/furnace/burn_progress");
    private static final int ARROW_U = 79;
    private static final int ARROW_V = 34;

    private VanillaGui() {
    }

    /** The container panel, any size, sliced from vanilla's. */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        int innerW = width - EDGE * 2;
        int innerH = height - EDGE * 2;
        int srcInnerW = SRC_W - EDGE * 2;
        int srcInnerH = SRC_H - EDGE * 2;

        blit(graphics, x + EDGE, y + EDGE, BODY_U, BODY_V, innerW, innerH, BODY_SIZE, BODY_SIZE);

        blit(graphics, x + EDGE, y, EDGE, 0, innerW, EDGE, srcInnerW, EDGE);
        blit(graphics, x + EDGE, y + height - EDGE, EDGE, SRC_H - EDGE, innerW, EDGE, srcInnerW, EDGE);
        blit(graphics, x, y + EDGE, 0, EDGE, EDGE, innerH, EDGE, srcInnerH);
        blit(graphics, x + width - EDGE, y + EDGE, SRC_W - EDGE, EDGE, EDGE, innerH, EDGE, srcInnerH);

        blit(graphics, x, y, 0, 0, EDGE, EDGE, EDGE, EDGE);
        blit(graphics, x + width - EDGE, y, SRC_W - EDGE, 0, EDGE, EDGE, EDGE, EDGE);
        blit(graphics, x, y + height - EDGE, 0, SRC_H - EDGE, EDGE, EDGE, EDGE, EDGE);
        blit(graphics, x + width - EDGE, y + height - EDGE,
            SRC_W - EDGE, SRC_H - EDGE, EDGE, EDGE, EDGE, EDGE);
    }

    /** One item slot, positioned by its 16x16 contents the way {@code Slot.x}/{@code Slot.y} are. */
    public static void slot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x - 1, y - 1, 18, 18);
    }

    /**
     * Vanilla's left-to-right progress arrow, empty outline plus however much of it has filled.
     *
     * <p>The same arrow every furnace in the game draws, so a player already knows what it means
     * without being told - which is the whole argument for borrowing chrome rather than inventing it.
     */
    public static void progressArrow(GuiGraphicsExtractor graphics, int x, int y, int filled) {
        blit(graphics, x, y, ARROW_U, ARROW_V, GuiTheme.ARROW_W, GuiTheme.ARROW_H,
            GuiTheme.ARROW_W, GuiTheme.ARROW_H);
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_FILL, GuiTheme.ARROW_W, 16, 0, 0,
                x, y, Math.min(filled, GuiTheme.ARROW_W), 16);
        }
    }

    /**
     * A recessed well, bevelled exactly like a slot.
     *
     * <p>Vanilla has no vertical gauge to borrow, so this borrows the one recess it does have. Gauge
     * contents belong inside the 1px frame: {@code x + 1, y + 1} to {@code x + w - 1, y + h - 1}.
     */
    public static void well(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, GuiTheme.SLOT_FACE);
        graphics.fill(x, y, x + width - 1, y + 1, GuiTheme.SLOT_SHADOW);
        graphics.fill(x, y, x + 1, y + height - 1, GuiTheme.SLOT_SHADOW);
        graphics.fill(x + width - 1, y, x + width, y + height, GuiTheme.SLOT_HIGHLIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, GuiTheme.SLOT_HIGHLIGHT);
    }

    /** A flat panel with a hard top and bottom edge, for a surface that is deliberately not vanilla's. */
    public static void slab(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            int body) {
        graphics.fill(x, y, x + width, y + height, body);
        graphics.fill(x, y, x + width, y + 1, GuiTheme.OUTLINE_DARK);
        graphics.fill(x, y + height - 1, x + width, y + height, GuiTheme.OUTLINE_DARK);
    }

    /** A border of the given thickness drawn just outside a rectangle - the picker's selection ring. */
    public static void border(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            int thickness, int colour) {
        graphics.fill(x, y, x + width, y + thickness, colour);
        graphics.fill(x, y + height - thickness, x + width, y + height, colour);
        graphics.fill(x, y, x + thickness, y + height, colour);
        graphics.fill(x + width - thickness, y, x + width, y + height, colour);
    }

    private static void blit(GuiGraphicsExtractor graphics, int x, int y, int u, int v,
            int width, int height, int srcWidth, int srcHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, x, y, u, v,
            width, height, srcWidth, srcHeight, TEX, TEX);
    }

    /** Blit an arbitrary vanilla container background, for a screen that reuses one wholesale. */
    public static void vanillaBackground(GuiGraphicsExtractor graphics, Identifier texture,
            int x, int y, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
            width, height, TEX, TEX);
    }
}
