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
    }

    /** One line: what the Hauler is doing, or that there is none. */
    private Component status() {
        if (!this.menu.hasHauler()) {
            return Component.translatable("container.recompile.hauler_status.none");
        }
        ScrapHaulerEntity.Mode mode = this.menu.haulerMode();
        if (mode == null) {
            return Component.translatable("container.recompile.hauler_status.docked",
                String.format("%,d", this.menu.haulerCharge()), String.format("%,d", this.menu.haulerCapacity()));
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
