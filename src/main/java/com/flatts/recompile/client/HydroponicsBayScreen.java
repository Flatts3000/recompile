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
 * <p><b>The chrome is vanilla's, not an imitation of it</b> ({@link VanillaGui}): the panel is nine-sliced
 * out of the furnace background, the slots are the {@code container/slot} sprite, and the grow arrow is
 * the furnace's own progress arrow. The first version of this screen hand-filled flat rectangles in
 * roughly the right greys and read as a grey box with holes in it - the bevels are what make a panel look
 * like Minecraft drew it, and they do not survive being re-derived by hand.
 *
 * <p>Both gauges are <b>always drawn</b>, empty or not. A bar that only appears once it has something in
 * it makes "this bay has no water" and "this screen has no water gauge" look identical, which is the
 * confusion the gauges exist to remove.
 */
public class HydroponicsBayScreen extends AbstractContainerScreen<HydroponicsBayMenu> {

    /** Vanilla's own water colour (the default biome water tint), and red for power. */
    private static final int WATER = 0xFF3F76E4;
    private static final int WATER_IDLE = 0xFF2A4E96;
    private static final int POWER = 0xFFE02B2B;
    private static final int POWER_IDLE = 0xFF8A1F1F;

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

        VanillaGui.panel(graphics, left, top, HydroponicsBayMenu.W, HydroponicsBayMenu.H);

        VanillaGui.slot(graphics, left + HydroponicsBayMenu.INPUT_X, top + HydroponicsBayMenu.INPUT_Y);
        VanillaGui.slot(graphics, left + HydroponicsBayMenu.OUTPUT_X, top + HydroponicsBayMenu.OUTPUT_Y);
        VanillaGui.slot(graphics,
            left + HydroponicsBayMenu.BYPRODUCT_X, top + HydroponicsBayMenu.BYPRODUCT_Y);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                VanillaGui.slot(graphics, left + HydroponicsBayMenu.INV_X + col * HydroponicsBayMenu.CELL,
                    top + HydroponicsBayMenu.INV_Y + row * HydroponicsBayMenu.CELL);
            }
        }
        for (int col = 0; col < 9; col++) {
            VanillaGui.slot(graphics, left + HydroponicsBayMenu.INV_X + col * HydroponicsBayMenu.CELL,
                top + HydroponicsBayMenu.HOTBAR_Y);
        }

        boolean running = this.menu.progress() > 0;
        gauge(graphics, left + HydroponicsBayMenu.WATER_X, top + HydroponicsBayMenu.GAUGE_Y,
            this.menu.water(), tankCapacity(), running ? WATER : WATER_IDLE);
        gauge(graphics, left + HydroponicsBayMenu.ENERGY_X, top + HydroponicsBayMenu.GAUGE_Y,
            this.menu.energy(), energyCapacity(), running ? POWER : POWER_IDLE);

        int goal = Math.max(1, this.menu.goal());
        VanillaGui.progressArrow(graphics,
            left + HydroponicsBayMenu.ARROW_X, top + HydroponicsBayMenu.ARROW_Y,
            HydroponicsBayMenu.ARROW_W * Math.min(this.menu.progress(), goal) / goal);
    }

    /**
     * A vertical bar that fills from the bottom.
     *
     * <p>Clamped, because a config change can leave stored energy above the capacity the gauge is
     * scaled to and a bar drawn past its own frame looks like a rendering fault rather than a full one.
     */
    private void gauge(GuiGraphicsExtractor graphics, int x, int y, int amount, int capacity, int colour) {
        VanillaGui.well(graphics, x, y, HydroponicsBayMenu.GAUGE_W, HydroponicsBayMenu.GAUGE_H);
        if (capacity <= 0 || amount <= 0) {
            return;
        }
        int inner = HydroponicsBayMenu.GAUGE_H - 2;
        int fill = Math.min(inner, inner * amount / capacity);
        graphics.fill(x + 1, y + HydroponicsBayMenu.GAUGE_H - 1 - fill,
            x + HydroponicsBayMenu.GAUGE_W - 1, y + HydroponicsBayMenu.GAUGE_H - 1, colour);
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
