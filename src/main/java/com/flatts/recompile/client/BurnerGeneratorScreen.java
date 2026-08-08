package com.flatts.recompile.client;

import com.flatts.recompile.content.block.entity.GeneratorState;
import com.flatts.recompile.content.menu.BurnerGeneratorMenu;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
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


    /**
     * Red, because that is what RF has looked like since Redstone Flux was named after redstone - every
     * tech mod a player has met draws energy red, and matching that costs nothing. Bright while running,
     * dark while idle, so a glance still says "is it working".
     */

    // Geometry lives on the menu, which is where the slots are placed from. Two copies of it is how the
    // first version of this screen drew its readout through the fuel row.
    private static final int W = BurnerGeneratorMenu.W;
    private static final int H = BurnerGeneratorMenu.H;
    private static final int CELL = BurnerGeneratorMenu.CELL;
    private static final int FUEL_X = BurnerGeneratorMenu.FUEL_X;
    private static final int FUEL_Y = BurnerGeneratorMenu.FUEL_Y;
    private static final int INV_Y = BurnerGeneratorMenu.INV_Y;
    private static final int HOTBAR_Y = BurnerGeneratorMenu.HOTBAR_Y;
    private static final int METER_X = BurnerGeneratorMenu.METER_X;
    private static final int METER_Y = BurnerGeneratorMenu.METER_Y;
    private static final int METER_W = BurnerGeneratorMenu.METER_W;
    private static final int METER_H = BurnerGeneratorMenu.METER_H;
    private static final int READOUT_Y = BurnerGeneratorMenu.READOUT_Y;

    public BurnerGeneratorScreen(BurnerGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 72;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float alpha) {
        super.extractBackground(graphics, mouseX, mouseY, alpha);
        int x = this.leftPos;
        int y = this.topPos;

        panel(graphics, x, y);

        for (int i = 0; i < BurnerGeneratorMenu.FUEL_SLOTS; i++) {
            slot(graphics, x + FUEL_X + i * CELL, y + FUEL_Y);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(graphics, x + BurnerGeneratorMenu.INV_X + col * CELL, y + INV_Y + row * CELL);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(graphics, x + BurnerGeneratorMenu.INV_X + col * CELL, y + HOTBAR_Y);
        }

        // The power meter.
        int capacity = Math.max(1, this.menu.energyCapacity());
        int stored = Math.max(0, Math.min(this.menu.energy(), capacity));
        recess(graphics, x + METER_X, y + METER_Y, METER_W, METER_H);
        int fill = (int) ((long) (METER_H - 2) * stored / capacity);
        if (fill > 0) {
            graphics.fill(x + METER_X + 1, y + METER_Y + METER_H - 1 - fill,
                x + METER_X + METER_W - 1, y + METER_Y + METER_H - 1,
                this.menu.isLit() ? VanillaGui.POWER : VanillaGui.POWER_IDLE);
        }
        // The number, because a bar says "roughly" and a player deciding whether to walk away wants
        // "exactly". One line UNDER the fuel row: beside the meter it collided with the slots, which is
        // how this screen shipped broken the first time.
        graphics.text(this.font, String.format("%,d / %,d FE", stored, capacity),
            x + FUEL_X, y + READOUT_Y, VanillaGui.TEXT_LABEL, false);
    }

    /**
     * Hover the meter for the exact figures.
     *
     * <p>The readout under the fuel row already shows stored-of-capacity, so the tooltip earns its place
     * by adding what the bar cannot: whether it is burning right now. A player looking at a half-full
     * meter wants to know if it is filling or draining, and neither the bar nor the number says.
     */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (!isHovering(METER_X, METER_Y, METER_W, METER_H, mouseX, mouseY)) {
            return;
        }
        int capacity = Math.max(1, this.menu.energyCapacity());
        int stored = Math.max(0, Math.min(this.menu.energy(), capacity));
        GeneratorState state = GeneratorState.of(stored, capacity, this.menu.isLit());
        List<Component> lines = List.of(
            Component.translatable("tooltip.recompile.energy_stored",
                String.format("%,d", stored), String.format("%,d", capacity)),
            Component.translatable(state.translationKey()).withStyle(
                state == GeneratorState.GENERATING ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    private static void panel(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + W, y + H, VanillaGui.PANEL_BODY);
        graphics.fill(x, y, x + W - 1, y + 2, VanillaGui.BEVEL_LIGHT);
        graphics.fill(x, y, x + 2, y + H - 1, VanillaGui.BEVEL_LIGHT);
        graphics.fill(x + 2, y + H - 2, x + W, y + H, VanillaGui.BEVEL_DARK);
        graphics.fill(x + W - 2, y + 2, x + W, y + H, VanillaGui.BEVEL_DARK);
    }

    private static void slot(GuiGraphicsExtractor graphics, int sx, int sy) {
        graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, VanillaGui.SLOT_FACE);
        graphics.fill(sx - 1, sy - 1, sx + 17, sy, VanillaGui.SLOT_SHADOW);
        graphics.fill(sx - 1, sy - 1, sx, sy + 17, VanillaGui.SLOT_SHADOW);
        graphics.fill(sx + 16, sy - 1, sx + 17, sy + 17, VanillaGui.BEVEL_LIGHT);
        graphics.fill(sx - 1, sy + 16, sx + 17, sy + 17, VanillaGui.BEVEL_LIGHT);
    }

    private static void recess(GuiGraphicsExtractor graphics, int rx, int ry, int w, int h) {
        graphics.fill(rx, ry, rx + w, ry + h, VanillaGui.SLOT_SHADOW);
        graphics.fill(rx, ry, rx + w - 1, ry + 1, VanillaGui.OUTLINE_DARK);
        graphics.fill(rx, ry, rx + 1, ry + h - 1, VanillaGui.OUTLINE_DARK);
    }
}
