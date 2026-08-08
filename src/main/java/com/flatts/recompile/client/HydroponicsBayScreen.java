package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.menu.HydroponicsBayMenu;
import com.flatts.recompile.gui.GuiTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * <p>Both gauges are <b>always drawn</b>, empty or not. A bar that only appears once it has something in
 * it makes "this bay has no water" and "this screen has no water gauge" look identical, which is the
 * confusion the gauges exist to remove. The well is chrome, so an empty gauge is still a visible gauge.
 */
public class HydroponicsBayScreen extends LayoutScreen<HydroponicsBayMenu> {

    public HydroponicsBayScreen(HydroponicsBayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, HydroponicsBayMenu.LAYOUT);
    }

    @Override
    protected void paint(GuiPainter painter, int mouseX, int mouseY) {
        boolean running = this.menu.progress() > 0;
        // Both capacities come off the menu, which the server fills in - never off the client's own
        // config. See HydroponicsBayMenu.DATA_WATER_CAPACITY for the two ways recomputing them is wrong.
        painter.gauge("water", this.menu.water(), this.menu.waterCapacity(),
            running ? GuiTheme.WATER : GuiTheme.WATER_IDLE);
        painter.gauge("power", this.menu.energy(), this.menu.energyCapacity(),
            running ? GuiTheme.POWER : GuiTheme.POWER_IDLE);
        painter.arrow("grow", this.menu.progress(), this.menu.goal());
    }

    /** Numbers on hover, because a bar says "some" and a player tuning a farm wants "how much". */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (isOver("water", mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font,
                Component.translatable("tooltip.recompile.hydroponics_water",
                    this.menu.water(), this.menu.waterCapacity()),
                mouseX, mouseY);
        } else if (isOver("power", mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font,
                Component.translatable("tooltip.recompile.hydroponics_power",
                    this.menu.energy(), this.menu.energyCapacity()),
                mouseX, mouseY);
        }
    }
}
