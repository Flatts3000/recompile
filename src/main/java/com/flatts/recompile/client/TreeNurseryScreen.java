package com.flatts.recompile.client;

import com.flatts.recompile.content.block.entity.TreeNurseryBlockEntity;
import com.flatts.recompile.content.menu.TreeNurseryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Tree Nursery's screen (spec {@code docs/tree_nursery_spec.md}): the mod's second bespoke screen.
 * Two inputs (Fertilizer, Unknown Seedling) feed a take-only sapling output; a <b>species picker</b>
 * (two rows) lets the player choose which vanilla sapling the machine raises, a tall water gauge reads
 * the tank, and a furnace-style cook arrow plus a live seconds countdown read the cook. Drawn
 * procedurally through 26.1's retained-mode "extract" model, in vanilla colours (bevelled beige panel,
 * recessed inset slots).
 */
public class TreeNurseryScreen extends AbstractContainerScreen<TreeNurseryMenu> {

    private static final int W = 176;
    private static final int H = 184;

    private static final int BODY = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SELECT = 0xFF7CFC00;

    /** The vanilla furnace bg (empty cook arrow lives at 79,34) and its cook-fill sprite. */
    private static final Identifier FURNACE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final Identifier BURN_PROGRESS = Identifier.withDefaultNamespace("container/furnace/burn_progress");

    private static final int FERT_X = 44;
    private static final int SEED_X = 62;
    private static final int OUT_X = 116;
    private static final int SLOT_Y = 24;
    private static final int GAUGE_X = 8;
    private static final int GAUGE_Y = 18;
    private static final int GAUGE_H = 56;
    private static final int ARROW_X = 84;
    private static final int ARROW_Y = 24;
    private static final int PICK_X = 52;
    private static final int PICK_Y = 46;
    private static final int PICK_COLS = 4;
    private static final int CELL = 18;

    public TreeNurseryScreen(TreeNurseryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.inventoryLabelX = 8;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float alpha) {
        super.extractBackground(graphics, mouseX, mouseY, alpha);
        int x = this.leftPos;
        int y = this.topPos;

        panel(graphics, x, y);

        slot(graphics, x + FERT_X, y + SLOT_Y);
        slot(graphics, x + SEED_X, y + SLOT_Y);
        slot(graphics, x + OUT_X, y + SLOT_Y);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(graphics, x + 8 + col * CELL, y + 102 + row * CELL);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(graphics, x + 8 + col * CELL, y + 160);
        }

        // Tall water gauge on the left, filling from the bottom.
        int cap = Math.max(1, this.menu.waterCapacity());
        int water = Math.max(0, this.menu.water());
        recess(graphics, x + GAUGE_X, y + GAUGE_Y, 8, GAUGE_H);
        int waterFill = (int) ((long) (GAUGE_H - 2) * water / cap);
        graphics.fill(x + GAUGE_X + 1, y + GAUGE_Y + GAUGE_H - 1 - waterFill,
            x + GAUGE_X + 7, y + GAUGE_Y + GAUGE_H - 1, 0xFF3A78C2);

        // The actual vanilla furnace cook arrow: empty from the furnace bg, filling from the burn sprite.
        int total = Math.max(1, this.menu.cookTotal());
        int progress = Math.max(0, Math.min(this.menu.cookProgress(), total));
        graphics.blit(RenderPipelines.GUI_TEXTURED, FURNACE, x + ARROW_X, y + ARROW_Y, 79.0F, 34.0F, 24, 16, 256, 256);
        int fillW = (progress * 24 + total - 1) / total;   // ceil(progress/total * 24), like vanilla
        if (fillW > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BURN_PROGRESS, 24, 16, 0, 0,
                x + ARROW_X, y + ARROW_Y, fillW, 16);
        }

        // Live countdown - whole seconds left on the current sapling, right of the output.
        if (this.menu.cookProgress() > 0) {
            int seconds = (total - progress + 19) / 20;
            graphics.text(this.font, seconds + "s", x + 138, y + 30, 0xFF404040, false);
        }

        // Species picker - two rows. The selected one is boxed bright green so it is unmistakable.
        int selected = this.menu.selectedSpecies();
        for (int i = 0; i < TreeNurseryBlockEntity.SPECIES.length; i++) {
            int px = x + PICK_X + (i % PICK_COLS) * CELL;
            int py = y + PICK_Y + (i / PICK_COLS) * CELL;
            slot(graphics, px, py);
            if (i == selected) {
                graphics.fill(px, py, px + 16, py + 16, 0x604CAF50);
                thickBorder(graphics, px - 2, py - 2, 20, 20, 2, SELECT);
            } else if (overSpecies(i, mouseX, mouseY)) {
                graphics.fill(px, py, px + 16, py + 16, 0x80FFFFFF);
            }
            graphics.item(new ItemStack(TreeNurseryBlockEntity.SPECIES[i]), px, py);
        }
    }

    private static void panel(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + W, y + H, BODY);
        graphics.fill(x, y, x + W - 1, y + 2, LIGHT);
        graphics.fill(x, y, x + 2, y + H - 1, LIGHT);
        graphics.fill(x + 2, y + H - 2, x + W, y + H, DARK);
        graphics.fill(x + W - 2, y + 2, x + W, y + H, DARK);
    }

    private static void slot(GuiGraphicsExtractor graphics, int sx, int sy) {
        graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, SLOT_BG);
        graphics.fill(sx - 1, sy - 1, sx + 17, sy, SLOT_SHADOW);
        graphics.fill(sx - 1, sy - 1, sx, sy + 17, SLOT_SHADOW);
        graphics.fill(sx + 16, sy - 1, sx + 17, sy + 17, LIGHT);
        graphics.fill(sx - 1, sy + 16, sx + 17, sy + 17, LIGHT);
    }

    private static void recess(GuiGraphicsExtractor graphics, int rx, int ry, int w, int h) {
        graphics.fill(rx, ry, rx + w, ry + h, SLOT_SHADOW);
        graphics.fill(rx, ry, rx + w - 1, ry + 1, 0xFF202020);
        graphics.fill(rx, ry, rx + 1, ry + h - 1, 0xFF202020);
    }

    private static void thickBorder(GuiGraphicsExtractor graphics, int bx, int by, int w, int h, int t, int color) {
        graphics.fill(bx, by, bx + w, by + t, color);
        graphics.fill(bx, by + h - t, bx + w, by + h, color);
        graphics.fill(bx, by, bx + t, by + h, color);
        graphics.fill(bx + w - t, by, bx + w, by + h, color);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        // Hover the water gauge to read the exact amount.
        int gx = this.leftPos + GAUGE_X;
        int gy = this.topPos + GAUGE_Y;
        if (mouseX >= gx && mouseX < gx + 8 && mouseY >= gy && mouseY < gy + GAUGE_H) {
            graphics.setTooltipForNextFrame(this.font, Component.translatable(
                "container.recompile.nursery_water", this.menu.water(), this.menu.waterCapacity()),
                mouseX, mouseY);
        }
    }

    private boolean overSpecies(int i, double mouseX, double mouseY) {
        int px = this.leftPos + PICK_X + (i % PICK_COLS) * CELL;
        int py = this.topPos + PICK_Y + (i / PICK_COLS) * CELL;
        return mouseX >= px && mouseX < px + 16 && mouseY >= py && mouseY < py + 16;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            for (int i = 0; i < TreeNurseryBlockEntity.SPECIES.length; i++) {
                if (overSpecies(i, event.x(), event.y())) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, i);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
