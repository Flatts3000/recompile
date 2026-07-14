package com.flatts.recompile.client.screen;

import com.flatts.recompile.content.block.entity.SortingTarpBlockEntity;
import com.flatts.recompile.content.menu.SortingTarpMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Sorting Tarp screen. Draws a plain panel + slot frames procedurally (no baked GUI
 * texture yet) and a progress bar; the base class renders the slots, labels, and
 * tooltips. Slot coordinates mirror {@link SortingTarpMenu}.
 */
public class SortingTarpScreen extends AbstractContainerScreen<SortingTarpMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int FRAME_DARK = 0xFF373737;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int BAR = 0xFF66AA66;
    private static final int BAR_BG = 0xFF444444;

    public SortingTarpScreen(SortingTarpMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.inventoryLabelX = 8;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        gui.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);

        // Slot frames: input, screen, six outputs, and the player inventory grid.
        frame(gui, x + 26, y + 20);
        frame(gui, x + 26, y + 48);
        for (int i = 0; i < SortingTarpBlockEntity.OUTPUT_COUNT; i++) {
            frame(gui, x + 98 + (i % 3) * 18, y + 17 + (i / 3) * 18);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                frame(gui, x + 8 + col * 18, y + 84 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            frame(gui, x + 8 + col * 18, y + 142);
        }

        // Progress bar between the input and the outputs.
        int total = this.menu.getTotal();
        int progress = this.menu.getProgress();
        int barX = x + 56;
        int barY = y + 30;
        gui.fill(barX, barY, barX + 32, barY + 6, BAR_BG);
        if (progress > 0 && total > 0) {
            int filled = Math.min(32, progress * 32 / total);
            gui.fill(barX, barY, barX + filled, barY + 6, BAR);
        }
    }

    private static void frame(GuiGraphicsExtractor gui, int sx, int sy) {
        gui.fill(sx - 1, sy - 1, sx + 17, sy + 17, FRAME_DARK);
        gui.fill(sx, sy, sx + 16, sy + 16, SLOT_BG);
    }
}
