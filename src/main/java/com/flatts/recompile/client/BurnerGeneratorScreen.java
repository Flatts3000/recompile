package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.block.entity.GeneratorState;
import com.flatts.recompile.content.menu.BurnerGeneratorMenu;
import com.flatts.recompile.gui.GuiTheme;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Burner Generator's screen (#72): a row of fuel slots and the power meter that is the whole reason
 * this screen exists.
 *
 * <p>The meter fills from the bottom and is <b>always drawn</b>, empty or not. A bar that only appears
 * once there is power in it would make "this generator has never run" and "this screen has no meter" look
 * identical, which is exactly the confusion the meter was added to remove.
 *
 * <p>This screen used to carry its own {@code panel()}, {@code slot()} and {@code recess()} - flat fills
 * that approximated vanilla rather than borrowing it, so it did not look like the Hydroponics Bay next
 * door. All three are gone: the chrome comes from the layout now, and the panel is vanilla's own,
 * nine-sliced.
 */
public class BurnerGeneratorScreen extends LayoutScreen<BurnerGeneratorMenu> {

    public BurnerGeneratorScreen(BurnerGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BurnerGeneratorMenu.LAYOUT);
    }

    @Override
    protected void paint(GuiPainter painter, int mouseX, int mouseY) {
        // Red, because that is what RF has looked like since Redstone Flux was named after redstone -
        // every tech mod a player has met draws energy red, and matching that costs nothing. Bright while
        // running, dark while idle, so a glance still says "is it working".
        painter.gauge("meter", stored(), capacity(),
            this.menu.isLit() ? GuiTheme.POWER : GuiTheme.POWER_IDLE);

        // The number, because a bar says "roughly" and a player deciding whether to walk away wants
        // "exactly". One line UNDER the fuel row: beside the meter it collided with the slots, which is
        // how this screen shipped broken the first time.
        painter.text("readout", String.format("%,d / %,d FE", stored(), capacity()),
            GuiTheme.TEXT_LABEL);
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
        if (!isOver("meter", mouseX, mouseY)) {
            return;
        }
        GeneratorState state = GeneratorState.of(stored(), capacity(), this.menu.isLit());
        List<Component> lines = List.of(
            Component.translatable("tooltip.recompile.energy_stored",
                String.format("%,d", stored()), String.format("%,d", capacity())),
            Component.translatable(state.translationKey()).withStyle(
                state == GeneratorState.GENERATING ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    private int capacity() {
        return Math.max(1, this.menu.energyCapacity());
    }

    private int stored() {
        return Math.max(0, Math.min(this.menu.energy(), capacity()));
    }
}
