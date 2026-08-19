package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.menu.CupolaFurnaceMenu;
import com.flatts.recompile.gui.GuiTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Cupola Furnace's screen: a furnace with a second output, for the slag (#236).
 *
 * <p><b>It is deliberately as close to vanilla's furnace as it can be.</b> The three slots a player
 * already knows are at vanilla's own coordinates, the flame sits between input and fuel, and the arrow
 * runs left to right - so the only thing on this screen a player has to learn is the extra slot on the
 * right. A bespoke screen is a cost, and the way to keep it small is to reuse everything about the
 * vanilla one except the part that could not be reused.
 *
 * <p>See {@code CupolaFurnaceMenu} for why the vanilla menu could not simply be subclassed: it calls
 * {@code checkContainerSize(container, 3)} and throws.
 */
public class CupolaFurnaceScreen extends LayoutScreen<CupolaFurnaceMenu> {

    public CupolaFurnaceScreen(CupolaFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CupolaFurnaceMenu.LAYOUT);
    }

    @Override
    protected void paint(GuiPainter painter, int mouseX, int mouseY) {
        // VANILLA'S FLAME, not a coloured bar in a recess.
        //
        // The first version used a well with a red fill, and a well is bevelled exactly like a slot -
        // so between the input and the fuel it read as a fourth slot, which is what playtest asked
        // about. Borrowing the flame costs nothing and every player in the game already knows what it
        // means, which is the same argument that got the arrow borrowed rather than invented.
        painter.flame("flame", Math.round(this.menu.burnProgress() * 100), 100);
        painter.arrow("cook", Math.round(this.menu.cookProgress() * 100), 100);
    }
}
