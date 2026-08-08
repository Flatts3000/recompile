package com.flatts.recompile.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Vanilla GUI chrome, drawn from vanilla's own assets.
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
 */
public final class VanillaGui {

    /** The panel source. Any vanilla container texture would do; the furnace is the plainest. */
    private static final Identifier PANEL = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
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
    public static final int ARROW_W = 24;
    public static final int ARROW_H = 17;

    /** Vanilla's slot palette, for the recessed wells that gauges live in. */
    public static final int SLOT_FACE = 0xFF8B8B8B;
    public static final int SLOT_SHADOW = 0xFF373737;
    public static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;

    /** The panel body, for the rare case something must be filled rather than nine-sliced. */
    public static final int PANEL_BODY = 0xFFC6C6C6;
    /** The two bevel tones, matching {@link #SLOT_HIGHLIGHT} and vanilla's shaded edge. */
    public static final int BEVEL_LIGHT = 0xFFFFFFFF;
    public static final int BEVEL_DARK = 0xFF555555;

    // ---------------- semantic colours ----------------
    //
    // Named for MEANING, not appearance, so "what colour is power" has exactly one answer. Before
    // this they were declared per screen: POWER and POWER_IDLE existed identically in both the
    // Hydroponics Bay and the Burner Generator, and the slot palette existed in three places at
    // once - here, and again in two screens that never called this class. They agreed only because
    // they were copy-pasted on one afternoon, and nothing tests colour, so a divergence would ship.

    /** A power bar with charge in it, and the same bar empty. */
    public static final int POWER = 0xFFE02B2B;
    public static final int POWER_IDLE = 0xFF8A1F1F;

    /** A water gauge with water in it, and the same gauge empty. */
    public static final int WATER = 0xFF3F76E4;
    public static final int WATER_IDLE = 0xFF2A4E96;

    /** Vanilla's label tone - the colour every container screen draws its title in. */
    public static final int TEXT_LABEL = 0xFF404040;
    /** The near-black outline vanilla draws around a recessed area. */
    public static final int OUTLINE_DARK = 0xFF202020;

    /** The highlight on a chosen entry in a picker. */
    public static final int SELECT = 0xFF7CFC00;

    // ---------------- vanilla's container metrics ----------------
    //
    // Vanilla's grammar, re-derived by every modder who has ever built a screen. These do not change
    // and there is no reason for a fifth copy of them to exist in a fifth file.

    /** The width every vanilla container panel has had since Beta. */
    public static final int PANEL_W = 176;
    /** The height of a container with one 3-row inventory and nothing above it. */
    public static final int PANEL_H = 166;
    /** Centre-to-centre distance between slots, and the drawn size of a slot's chrome. */
    public static final int SLOT_PITCH = 18;
    /** A slot's contents are 16x16; its bevel is drawn one pixel outside that. */
    public static final int SLOT_SIZE = 16;
    /** Where the player's 9x3 inventory starts in a standard 176x166 panel. */
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;
    /** Where the hotbar row sits in the same panel. */
    public static final int HOTBAR_Y = 142;
    /** The title, and the "Inventory" label above the player's grid. */
    public static final int TITLE_X = 8;
    public static final int TITLE_Y = 6;

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
        blit(graphics, x, y, ARROW_U, ARROW_V, ARROW_W, ARROW_H, ARROW_W, ARROW_H);
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_FILL, ARROW_W, 16, 0, 0,
                x, y, Math.min(filled, ARROW_W), 16);
        }
    }

    /**
     * A recessed well, bevelled exactly like a slot.
     *
     * <p>Vanilla has no vertical gauge to borrow, so this borrows the one recess it does have. Gauge
     * contents belong inside the 1px frame: {@code x + 1, y + 1} to {@code x + w - 1, y + h - 1}.
     */
    public static void well(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, SLOT_FACE);
        graphics.fill(x, y, x + width - 1, y + 1, SLOT_SHADOW);
        graphics.fill(x, y, x + 1, y + height - 1, SLOT_SHADOW);
        graphics.fill(x + width - 1, y, x + width, y + height, SLOT_HIGHLIGHT);
        graphics.fill(x, y + height - 1, x + width, y + height, SLOT_HIGHLIGHT);
    }

    private static void blit(GuiGraphicsExtractor graphics, int x, int y, int u, int v,
            int width, int height, int srcWidth, int srcHeight) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, x, y, u, v,
            width, height, srcWidth, srcHeight, TEX, TEX);
    }
}
