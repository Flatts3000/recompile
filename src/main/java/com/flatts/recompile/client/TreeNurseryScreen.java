package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.block.entity.TreeNurseryBlockEntity;
import com.flatts.recompile.content.menu.TreeNurseryMenu;
import com.flatts.recompile.gui.GuiTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Tree Nursery's screen: two inputs feed a take-only sapling output, a <b>species picker</b> chooses
 * which vanilla sapling the machine raises, a tall water gauge reads the tank, and a furnace cook arrow
 * plus a live seconds countdown read the cook.
 *
 * <p>The picker is the reason this screen exists at all: species selection has no vanilla-screen analog
 * and cannot be an inserted-item template, because saplings cannot be held as an input - that is the
 * whole loot strip.
 *
 * <p>Its tank used to be drawn in a blue of its own while the Hydroponics Bay drew the same water in a
 * different one - two colours for one substance, in a mod where both machines share a water economy, and
 * neither file could see the other. Both now read {@link GuiTheme#WATER}.
 */
public class TreeNurseryScreen extends LayoutScreen<TreeNurseryMenu> {

    public TreeNurseryScreen(TreeNurseryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TreeNurseryMenu.LAYOUT);
    }

    @Override
    protected void paint(GuiPainter painter, int mouseX, int mouseY) {
        painter.gauge("water", this.menu.water(), this.menu.waterCapacity(), GuiTheme.WATER);
        painter.arrow("cook", this.menu.cookProgress(), this.menu.cookTotal());

        // Whole seconds left on the current sapling. The arrow reads as a proportion, and the decision it
        // drives - can I walk away - is answered by seconds.
        if (this.menu.cookProgress() > 0) {
            painter.text("countdown", secondsLeft() + "s", GuiTheme.TEXT_LABEL);
        }

        // The picker. The selected species is boxed bright green so it is unmistakable, and the hovered
        // one washes lighter.
        int selected = this.menu.selectedSpecies();
        for (int i = 0; i < TreeNurseryBlockEntity.SPECIES.length; i++) {
            if (i == selected) {
                painter.tint("species", i, GuiTheme.SELECT_TINT);
                painter.ring("species", i, 2, 2, GuiTheme.SELECT);
            } else if (painter.isOver("species", i, mouseX, mouseY)) {
                painter.tint("species", i, GuiTheme.HOVER_CELL);
            }
            painter.item("species", i, new ItemStack(TreeNurseryBlockEntity.SPECIES[i]));
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        // Hover the water gauge to read the exact amount.
        if (isOver("water", mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, Component.translatable(
                "container.recompile.nursery_water", this.menu.water(), this.menu.waterCapacity()),
                mouseX, mouseY);
            return;
        }
        // ...and the cook arrow, which had none.
        if (isOver("cook", mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, this.menu.cookProgress() > 0
                ? Component.translatable("tooltip.recompile.cook_remaining", secondsLeft())
                : Component.translatable("tooltip.recompile.cook_idle").withStyle(ChatFormatting.DARK_GRAY),
                mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            int picked = overIndex("species", TreeNurseryBlockEntity.SPECIES.length,
                event.x(), event.y());
            if (picked >= 0) {
                // The vanilla Stonecutter/Loom path: the id travels as a VAR_INT, no custom packet.
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, picked);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * The countdown, straight off the menu.
     *
     * <p>It used to be computed here from progress and total in TICKS, which was wrong twice over: the
     * total came off the client's own COMMON config, which NeoForge does not sync, and neither number
     * fitted the 16-bit wire once a pack raised {@code treeNurseryCookTicks} (#369). The server sends
     * seconds now, because seconds are what this line prints.
     */
    private int secondsLeft() {
        return this.menu.secondsLeft();
    }
}
