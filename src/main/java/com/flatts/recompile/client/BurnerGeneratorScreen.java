package com.flatts.recompile.client;

import com.flatts.recompile.content.menu.BurnerGeneratorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Burner Generator's screen (#72): a row of fuel slots and the power meter that is the whole reason
 * this screen exists.
 *
 * <p>Drawn procedurally in vanilla colours through 26.1's retained-mode "extract" model, the same way
 * {@link TreeNurseryScreen} is - no new GUI texture, so the art budget stays on blocks.
 *
 * <p>The meter fills from the bottom and is <b>always drawn</b>, empty or not. A bar that only appears
 * once there is power in it would make "this generator has never run" and "this screen has no meter" look
 * identical, which is exactly the confusion the meter was added to remove.
 */
public class BurnerGeneratorScreen extends AbstractContainerScreen<BurnerGeneratorMenu> {

    private static final int W = 176;
    private static final int H = 133;

    private static final int BODY = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;

    /** Flame orange when running, dull ember when idle - so a glance says "is it working". */
    private static final int POWER_LIT = 0xFFFF9A2B;
    private static final int POWER_IDLE = 0xFF8A5A2B;

    private static final int METER_X = 16;
    private static final int METER_Y = 16;
    private static final int METER_W = 12;
    private static final int METER_H = 26;
    private static final int CELL = 18;

    public BurnerGeneratorScreen(BurnerGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = H - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float alpha) {
        super.extractBackground(graphics, mouseX, mouseY, alpha);
        int x = this.leftPos;
        int y = this.topPos;

        panel(graphics, x, y);

        for (int i = 0; i < BurnerGeneratorMenu.FUEL_SLOTS; i++) {
            slot(graphics, x + 44 + i * CELL, y + 20);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(graphics, x + 8 + col * CELL, y + 51 + row * CELL);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(graphics, x + 8 + col * CELL, y + 109);
        }

        // The power meter.
        int capacity = Math.max(1, this.menu.energyCapacity());
        int stored = Math.max(0, Math.min(this.menu.energy(), capacity));
        recess(graphics, x + METER_X, y + METER_Y, METER_W, METER_H);
        int fill = (int) ((long) (METER_H - 2) * stored / capacity);
        if (fill > 0) {
            graphics.fill(x + METER_X + 1, y + METER_Y + METER_H - 1 - fill,
                x + METER_X + METER_W - 1, y + METER_Y + METER_H - 1,
                this.menu.isLit() ? POWER_LIT : POWER_IDLE);
        }
        // The number, because a bar says "roughly" and a player deciding whether to walk away wants
        // "exactly". Grouped digits read faster than a run of numerals.
        graphics.text(this.font, String.format("%,d FE", stored), x + METER_X + METER_W + 6, y + METER_Y + 4,
            0xFF404040, false);
        graphics.text(this.font, String.format("of %,d", capacity), x + METER_X + METER_W + 6,
            y + METER_Y + 14, 0xFF707070, false);
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
}
