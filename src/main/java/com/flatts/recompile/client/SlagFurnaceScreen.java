package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.menu.SlagFurnaceMenu;
import com.flatts.recompile.gui.GuiTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Slag Furnace's screen: vanilla furnace geometry, drawn through this mod's GUI framework.
 *
 * <p><b>Vanilla's exact coordinates.</b> Input at (56,17), fuel at (56,53), the flame between them,
 * the arrow at (79,34) and the result at (116,35) - the numbers vanilla itself uses. A player who has
 * opened a furnace knows this screen already.
 *
 * <p><b>It is not a recipe-book screen, and an earlier version of this file claimed it was.</b>
 * {@link LayoutScreen} extends {@code AbstractContainerScreen}, not {@code AbstractRecipeBookScreen},
 * so there is no book button and no ghost slots - the book widget comes from the SCREEN, not from the
 * menu, whatever the menu's {@code RecipeBookType} says. The same correction applies to JEI's furnace
 * transfer button, which its built-in handler keys to vanilla's own menu classes rather than to any
 * subclass of {@code AbstractFurnaceMenu}. Neither integration is inherited by subclassing the menu.
 *
 * <p>What subclassing {@code AbstractFurnaceMenu} DOES buy is real and is the whole reason for it: the
 * slots, {@code quickMoveStack}, the fuel/progress data sync and the container plumbing all come for
 * free, where the Cupola had to reimplement every one of them over a bare {@code AbstractContainerMenu}
 * because its fourth slot trips {@code checkContainerSize(container, 3)}. That is a smaller claim than
 * the one this file used to make, and it is the true one.
 *
 * <p>The layout lives on {@link SlagFurnaceMenu} rather than here, because a screen class cannot be
 * loaded server-side and the geometry sweeps run on a dedicated server.
 */
public class SlagFurnaceScreen extends LayoutScreen<SlagFurnaceMenu> {

    public SlagFurnaceScreen(SlagFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SlagFurnaceMenu.LAYOUT);
    }

    @Override
    protected void paint(GuiPainter painter, int mouseX, int mouseY) {
        // Both menu accessors return a 0..1 fraction; the painters take whole pixels, and rounding
        // rather than truncating is what stops a nearly-full flame reading as one pixel short forever.
        painter.flame("flame", Math.round(this.menu.getLitProgress() * GuiTheme.FLAME_H),
            GuiTheme.FLAME_H);
        painter.arrow("cook", Math.round(this.menu.getBurnProgress() * GuiTheme.ARROW_W),
            GuiTheme.ARROW_W);
    }
}
