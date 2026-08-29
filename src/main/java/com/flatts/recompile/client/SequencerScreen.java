package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.menu.SequencerMenu;
import com.flatts.recompile.gui.GuiTheme;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Sequencer's screen (#294): amber in, fragment out, and the power meter that is the reason this
 * machine owns a screen at all.
 *
 * <p><b>The eighth custom screen in this mod, and the same recorded exception as the third.</b>
 * Containers reuse a vanilla screen; a machine that burns FE needs an energy bar and no vanilla screen
 * has one. That is precisely why the Burner Generator got one in #72, so this adds a case to a
 * standing exception rather than opening a new kind of one.
 *
 * <p>The meter is <b>always drawn</b>, empty or not, for the reason the generator's is: a bar that
 * appears only once there is power in it makes "this machine has never run" and "this screen has no
 * meter" look identical.
 */
public class SequencerScreen extends LayoutScreen<SequencerMenu> {

    public SequencerScreen(SequencerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SequencerMenu.LAYOUT);
    }

    @Override
    protected void paint(GuiPainter painter, int mouseX, int mouseY) {
        // Red, matching the generators, because a player reading two of this mod's screens should not
        // have to learn two colours for the same quantity.
        painter.gauge("meter", stored(), capacity(),
            this.menu.progressTicks() > 0 ? GuiTheme.POWER : GuiTheme.POWER_IDLE);
        painter.arrow("progress", this.menu.progressTicks(), this.menu.ticksPerRead());
    }

    /**
     * Hover the meter for the exact figures.
     *
     * <p>There is no readout line under the slots the way the generator has one - this panel is a
     * furnace shape with an arrow through the middle of it, and a line of text there would sit on the
     * arrow. The tooltip carries the numbers instead.
     */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (!isOver("meter", mouseX, mouseY)) {
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
