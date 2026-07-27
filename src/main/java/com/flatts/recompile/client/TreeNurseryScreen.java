package com.flatts.recompile.client;

import com.flatts.recompile.content.block.entity.TreeNurseryBlockEntity;
import com.flatts.recompile.content.menu.TreeNurseryMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Tree Nursery's screen (spec {@code docs/tree_nursery_spec.md}): the mod's second bespoke screen.
 * Two inputs (Fertilizer, Unknown Seedling) feed a take-only sapling output; a <b>species picker</b>
 * row lets the player choose which vanilla sapling the machine raises, and a water gauge + progress
 * arrow read the cook. The picker is the whole reason this needs a custom screen - no vanilla screen
 * carries selectable-mode buttons, and the choice cannot be an inserted item (saplings cannot be held).
 *
 * <p>Drawn procedurally (no GUI texture yet - art is a texgen follow-up), through 26.1's retained-mode
 * "extract" model: {@link #extractBackground} via {@link GuiGraphicsExtractor}.
 */
public class TreeNurseryScreen extends AbstractContainerScreen<TreeNurseryMenu> {

    private static final int W = 176;
    private static final int H = 166;
    private static final int PICK_X = 16;
    private static final int PICK_Y = 57;
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

        graphics.fill(x, y, x + W, y + H, 0xFFC6C6C6);            // panel body
        graphics.fill(x, y, x + W, y + 1, 0xFF8B8B8B);            // top edge
        graphics.fill(x, y + H - 1, x + W, y + H, 0xFF8B8B8B);    // bottom edge

        // The machine slots: Fertilizer, Unknown Seedling, and the take-only sapling output.
        slot(graphics, x + 44, y + 35);
        slot(graphics, x + 62, y + 35);
        slot(graphics, x + 116, y + 35);
        // Player inventory + hotbar recesses.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(graphics, x + 8 + col * CELL, y + 84 + row * CELL);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(graphics, x + 8 + col * CELL, y + 142);
        }

        // Water gauge (fills from the bottom).
        int cap = Math.max(1, this.menu.waterCapacity());
        int water = Math.max(0, this.menu.water());
        int gaugeX = x + 8;
        int gaugeY = y + 20;
        int gaugeH = 46;
        graphics.fill(gaugeX, gaugeY, gaugeX + 8, gaugeY + gaugeH, 0xFF303030);
        int waterFill = (int) ((long) gaugeH * water / cap);
        graphics.fill(gaugeX, gaugeY + gaugeH - waterFill, gaugeX + 8, gaugeY + gaugeH, 0xFF3A78C2);

        // Progress arrow (Fertilizer + Seedling -> sapling).
        int total = Math.max(1, this.menu.cookTotal());
        int progress = Math.max(0, this.menu.cookProgress());
        int arrowX = x + 84;
        int arrowY = y + 40;
        int arrowW = 24;
        graphics.fill(arrowX, arrowY, arrowX + arrowW, arrowY + 5, 0xFF555555);
        graphics.fill(arrowX, arrowY, arrowX + (int) ((long) arrowW * progress / total), arrowY + 5, 0xFF57A957);

        // The species picker: one sapling icon per species; the selected one is boxed green.
        int selected = this.menu.selectedSpecies();
        for (int i = 0; i < TreeNurseryBlockEntity.SPECIES.length; i++) {
            int px = x + PICK_X + i * CELL;
            int py = y + PICK_Y;
            int frame = 0xFF373737;
            if (i == selected) {
                frame = 0xFF57A957;
            } else if (overSpecies(i, mouseX, mouseY)) {
                frame = 0x80FFFFFF;
            }
            graphics.fill(px - 1, py - 1, px + 17, py + 17, frame);
            graphics.item(new ItemStack(TreeNurseryBlockEntity.SPECIES[i]), px, py);
        }
    }

    private static void slot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
    }

    private boolean overSpecies(int i, double mouseX, double mouseY) {
        int px = this.leftPos + PICK_X + i * CELL;
        int py = this.topPos + PICK_Y;
        return mouseX >= px && mouseX < px + 16 && mouseY >= py && mouseY < py + 16;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            for (int i = 0; i < TreeNurseryBlockEntity.SPECIES.length; i++) {
                if (overSpecies(i, event.x(), event.y())) {
                    // The button id IS the species index; the server sets it on the nursery BE.
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, i);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
