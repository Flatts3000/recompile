package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.menu.SinteringKilnMenu;
import com.flatts.recompile.gui.GuiTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Sintering Kiln's screen: vanilla furnace geometry, drawn through this mod's GUI framework.
 *
 * <p>Vanilla's exact coordinates - input at (56,17), fuel at (56,53), the flame between them, the
 * arrow at (79,34) and the result at (116,35). A player who has opened a furnace knows this screen.
 *
 * <p>No recipe book and no JEI transfer button: {@link LayoutScreen} extends
 * {@code AbstractContainerScreen} rather than {@code AbstractRecipeBookScreen}, and both integrations
 * belong to the screen rather than to the menu whatever its {@code RecipeBookType} says. That is
 * deliberate here - the owner made "no recipe-book buttons in this mod's machines" standing on
 * 2026-08-19.
 *
 * <p>The layout lives on {@link SinteringKilnMenu} rather than here, because a screen class cannot be
 * loaded server-side and the geometry sweeps run on a dedicated server.
 */
public class SinteringKilnScreen extends LayoutScreen<SinteringKilnMenu> {

    public SinteringKilnScreen(SinteringKilnMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SinteringKilnMenu.LAYOUT);
    }

    @Override
    protected void paint(GuiPainter painter, int mouseX, int mouseY) {
        // Both accessors return a 0..1 fraction and the painters take whole pixels; rounding rather
        // than truncating stops a nearly-full flame reading one pixel short forever.
        painter.flame("flame", Math.round(this.menu.getLitProgress() * GuiTheme.FLAME_H),
            GuiTheme.FLAME_H);
        painter.arrow("cook", Math.round(this.menu.getBurnProgress() * GuiTheme.ARROW_W),
            GuiTheme.ARROW_W);
    }
}
