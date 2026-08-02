package com.flatts.recompile.client;

import com.flatts.recompile.content.block.entity.HydroponicsBayBlockEntity;
import com.flatts.recompile.content.menu.HydroponicsBayMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Hydroponics Bay's screen (#43).
 *
 * <p><b>Two gauges is the whole reason it exists.</b> This machine is the only one in the mod that
 * consumes water AND power at once, so "why is it not running" has two possible answers and a player
 * needs to see both at a glance. The alternative considered was a chest screen plus Jade, which would
 * have obeyed the existing "containers reuse a vanilla screen" rule; the owner called for a real GUI
 * (2026-08-02) because three resources on hover is worse than three gauges in front of you.
 *
 * <p>Drawn procedurally in vanilla colours through 26.1's retained-mode extract model, the same as
 * {@link BurnerGeneratorScreen} and {@link TreeNurseryScreen} - no new GUI texture, so the art budget
 * stays on blocks.
 *
 * <p>Both gauges are <b>always drawn</b>, empty or not. A bar that only appears once it has something in
 * it makes "this bay has no water" and "this screen has no water gauge" look identical, which is the
 * confusion the gauges exist to remove.
 */
public class HydroponicsBayScreen extends AbstractContainerScreen<HydroponicsBayMenu> {

    private static final int BODY = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int DARK = 0xFF555555;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int GAUGE_BG = 0xFF373737;

    /** Water blue, and red for power - the colour every tech mod has used since Redstone Flux. */
    private static final int WATER = 0xFF3B6FD4;
    private static final int WATER_IDLE = 0xFF25457F;
    private static final int POWER = 0xFFE02B2B;
    private static final int POWER_IDLE = 0xFF8A1F1F;
    /** The grow arrow, green because that is what is happening. */
    private static final int ARROW_BG = 0xFF6B6B6B;
    private static final int ARROW = 0xFF4CAF50;

    public HydroponicsBayScreen(HydroponicsBayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, HydroponicsBayMenu.W, HydroponicsBayMenu.H);
    }

    @Override
    protected void init() {
        super.init();
        this.inventoryLabelY = HydroponicsBayMenu.INV_Y - 12;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int left = this.leftPos;
        int top = this.topPos;

        graphics.fill(left, top, left + HydroponicsBayMenu.W, top + HydroponicsBayMenu.H, BODY);
        graphics.fill(left, top, left + HydroponicsBayMenu.W, top + 1, LIGHT);
        graphics.fill(left, top, left + 1, top + HydroponicsBayMenu.H, LIGHT);
        graphics.fill(left, top + HydroponicsBayMenu.H - 1,
            left + HydroponicsBayMenu.W, top + HydroponicsBayMenu.H, DARK);
        graphics.fill(left + HydroponicsBayMenu.W - 1, top,
            left + HydroponicsBayMenu.W, top + HydroponicsBayMenu.H, DARK);

        slot(graphics, left + HydroponicsBayMenu.INPUT_X, top + HydroponicsBayMenu.INPUT_Y);
        slot(graphics, left + HydroponicsBayMenu.OUTPUT_X, top + HydroponicsBayMenu.OUTPUT_Y);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(graphics, left + HydroponicsBayMenu.INV_X + col * HydroponicsBayMenu.CELL,
                    top + HydroponicsBayMenu.INV_Y + row * HydroponicsBayMenu.CELL);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(graphics, left + HydroponicsBayMenu.INV_X + col * HydroponicsBayMenu.CELL,
                top + HydroponicsBayMenu.HOTBAR_Y);
        }

        boolean running = this.menu.progress() > 0;
        gauge(graphics, left + HydroponicsBayMenu.WATER_X, top + HydroponicsBayMenu.GAUGE_Y,
            this.menu.water(), tankCapacity(), running ? WATER : WATER_IDLE);
        gauge(graphics, left + HydroponicsBayMenu.ENERGY_X, top + HydroponicsBayMenu.GAUGE_Y,
            this.menu.energy(), energyCapacity(), running ? POWER : POWER_IDLE);

        // The grow arrow fills left to right as the batch runs.
        int ax = left + HydroponicsBayMenu.ARROW_X;
        int ay = top + HydroponicsBayMenu.ARROW_Y;
        graphics.fill(ax, ay + 5, ax + HydroponicsBayMenu.ARROW_W, ay + 11, ARROW_BG);
        int goal = Math.max(1, this.menu.goal());
        int filled = HydroponicsBayMenu.ARROW_W * Math.min(this.menu.progress(), goal) / goal;
        if (filled > 0) {
            graphics.fill(ax, ay + 5, ax + filled, ay + 11, ARROW);
        }
    }

    /**
     * A vertical bar that fills from the bottom.
     *
     * <p>Clamped, because a config change can leave stored energy above the capacity the gauge is
     * scaled to and a bar drawn past its own frame looks like a rendering fault rather than a full one.
     */
    private void gauge(GuiGraphicsExtractor graphics, int x, int y, int amount, int capacity, int colour) {
        graphics.fill(x, y, x + HydroponicsBayMenu.GAUGE_W, y + HydroponicsBayMenu.GAUGE_H, GAUGE_BG);
        if (capacity <= 0 || amount <= 0) {
            return;
        }
        int fill = Math.min(HydroponicsBayMenu.GAUGE_H,
            HydroponicsBayMenu.GAUGE_H * amount / capacity);
        graphics.fill(x + 1, y + HydroponicsBayMenu.GAUGE_H - fill,
            x + HydroponicsBayMenu.GAUGE_W - 1, y + HydroponicsBayMenu.GAUGE_H, colour);
    }

    private void slot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SHADOW);
        graphics.fill(x, y, x + 16, y + 16, SLOT_BG);
    }

    private int tankCapacity() {
        return com.flatts.recompile.RCConfig.HYDROPONICS_TANK_CAPACITY.get();
    }

    private int energyCapacity() {
        return com.flatts.recompile.RCConfig.HYDROPONICS_GROW_TICKS.get()
            * com.flatts.recompile.RCConfig.HYDROPONICS_FE_PER_TICK.get();
    }

    /** Numbers on hover, because a bar says "some" and a player tuning a farm wants "how much". */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (over(mouseX, mouseY, HydroponicsBayMenu.WATER_X)) {
            graphics.setTooltipForNextFrame(this.font,
                Component.translatable("tooltip.recompile.hydroponics_water",
                    this.menu.water(), tankCapacity()),
                mouseX, mouseY);
        } else if (over(mouseX, mouseY, HydroponicsBayMenu.ENERGY_X)) {
            graphics.setTooltipForNextFrame(this.font,
                Component.translatable("tooltip.recompile.hydroponics_power",
                    this.menu.energy(), energyCapacity()),
                mouseX, mouseY);
        }
    }

    private boolean over(int mouseX, int mouseY, int gaugeX) {
        int x = this.leftPos + gaugeX;
        int y = this.topPos + HydroponicsBayMenu.GAUGE_Y;
        return mouseX >= x && mouseX < x + HydroponicsBayMenu.GAUGE_W
            && mouseY >= y && mouseY < y + HydroponicsBayMenu.GAUGE_H;
    }
}
