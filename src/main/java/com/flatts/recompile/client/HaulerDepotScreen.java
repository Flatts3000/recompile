package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.entity.ScrapHaulerEntity;
import com.flatts.recompile.content.menu.HaulerDepotMenu;
import com.flatts.recompile.gui.GuiTheme;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Hauler Depot's screen (#376): the eleventh custom screen in this mod, and a recorded reversal.
 *
 * <p>Containers reuse a vanilla screen and the Charging Station has none; this is both a container
 * and a dock, plus a gauge and a button, and vanilla has no screen shaped like that. See
 * {@code HaulerDepotBlock}.
 *
 * <p><b>One button, two states</b> (ruling 14): Deploy while docked, Recall while out. It is drawn
 * from the synced flag and clicked through vanilla's Stonecutter/Loom path, so there is no custom
 * packet and the server decides which transition it actually is.
 */
public class HaulerDepotScreen extends LayoutScreen<HaulerDepotMenu> {

    public HaulerDepotScreen(HaulerDepotMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, HaulerDepotMenu.LAYOUT);
    }

    @Override
    protected void paint(GuiPainter painter, int mouseX, int mouseY) {
        // Red, like every other power gauge here: one colour for one quantity across the mod.
        painter.gauge("power", stored(), capacity(), this.menu.deployed() ? GuiTheme.POWER : GuiTheme.POWER_IDLE);

        boolean live = this.menu.hasHauler();
        painter.slab("deploy", live && painter.isOver("deploy", mouseX, mouseY)
            ? GuiTheme.SLOT_HIGHLIGHT : (live ? GuiTheme.SLOT_FACE : GuiTheme.SLOT_SHADOW));
        painter.text("deploy_label", Component.translatable(this.menu.deployed()
            ? "container.recompile.hauler_recall" : "container.recompile.hauler_deploy").getString(),
            live ? GuiTheme.TEXT_LABEL : GuiTheme.TEXT_MUTED);

        painter.text("status", status().getString(), this.menu.deployed() ? GuiTheme.TEXT_GOOD : GuiTheme.TEXT_LABEL);

        // The work area. Two small buttons and the square they make, in chunks.
        int max = com.flatts.recompile.content.block.entity.HaulerDepotBlockEntity.maxChunkRadius();
        int r = this.menu.chunkRadius();
        painter.slab("radius_down", r > 0 && painter.isOver("radius_down", mouseX, mouseY)
            ? GuiTheme.SLOT_HIGHLIGHT : (r > 0 ? GuiTheme.SLOT_FACE : GuiTheme.SLOT_SHADOW));
        painter.text("radius_down_label", "-", r > 0 ? GuiTheme.TEXT_LABEL : GuiTheme.TEXT_MUTED);
        painter.slab("radius_up", r < max && painter.isOver("radius_up", mouseX, mouseY)
            ? GuiTheme.SLOT_HIGHLIGHT : (r < max ? GuiTheme.SLOT_FACE : GuiTheme.SLOT_SHADOW));
        painter.text("radius_up_label", "+", r < max ? GuiTheme.TEXT_LABEL : GuiTheme.TEXT_MUTED);
        int side = 2 * r + 1;
        painter.text("radius_label", side + "x" + side, GuiTheme.TEXT_LABEL);
    }

    /** One line: what the Hauler is doing, or that there is none. */
    private Component status() {
        if (!this.menu.hasHauler()) {
            return Component.translatable("container.recompile.hauler_status.none");
        }
        ScrapHaulerEntity.Mode mode = this.menu.haulerMode();
        if (mode == null) {
            // Charge only. The capacity used to be printed beside it, and "Docked, 16,000 / 16,000 FE"
            // was the longest of the seven status lines - it ran out of its own region and under the
            // power gauge. The gauge next to this line already shows how full it is.
            return Component.translatable("container.recompile.hauler_status.docked",
                String.format("%,d", this.menu.haulerCharge()));
        }
        String key = switch (mode) {
            case SEEKING -> "container.recompile.hauler_status.seeking";
            case RETURNING -> "container.recompile.hauler_status.returning";
            case DUMPING, WAITING_DEPOT -> "container.recompile.hauler_status.dumping";
            case PARKED_FLAT -> "container.recompile.hauler_status.flat";
            case PARKED_IDLE -> "container.recompile.hauler_status.idle";
        };
        return Component.translatable(key, this.menu.haulerCargo(), ScrapHaulerEntity.CARGO_CAPACITY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isOver("radius_down", event.x(), event.y())) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, HaulerDepotMenu.RADIUS_DOWN_BUTTON);
            return true;
        }
        if (event.button() == 0 && isOver("radius_up", event.x(), event.y())) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, HaulerDepotMenu.RADIUS_UP_BUTTON);
            return true;
        }
        if (event.button() == 0 && this.menu.hasHauler() && isOver("deploy", event.x(), event.y())) {
            // Send what the button SAID, so the server can ignore a click that no longer applies.
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                this.menu.deployed() ? HaulerDepotMenu.RECALL_BUTTON : HaulerDepotMenu.DEPLOY_BUTTON);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (isOver("radius_label", mouseX, mouseY) || isOver("radius_down", mouseX, mouseY)
                || isOver("radius_up", mouseX, mouseY)) {
            int side = 2 * this.menu.chunkRadius() + 1;
            int maxSide = 2 * com.flatts.recompile.content.block.entity.HaulerDepotBlockEntity.maxChunkRadius() + 1;
            graphics.setTooltipForNextFrame(this.font, List.of(
                Component.translatable("container.recompile.hauler_radius", side, side),
                Component.translatable("container.recompile.hauler_radius_max", maxSide, maxSide)),
                Optional.empty(), mouseX, mouseY);
            return;
        }
        if (!isOver("power", mouseX, mouseY)) {
            return;
        }
        List<Component> lines = List.of(Component.translatable("tooltip.recompile.energy_stored",
            String.format("%,d", stored()), String.format("%,d", capacity())));
        graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    private int capacity() {
        return Math.max(1, this.menu.energyCapacity());
    }

    private int stored() {
        return Math.max(0, Math.min(this.menu.energy(), capacity()));
    }
}
