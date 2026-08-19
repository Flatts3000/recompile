package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.menu.SlagFurnaceMenu;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.ScreenLayout;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Slag Furnace's screen: a furnace, drawn like every other furnace in the game.
 *
 * <p><b>Vanilla's exact geometry.</b> Input at (56,17), fuel at (56,53), the flame between them, the
 * arrow at (79,34) and the result at (116,35) - the numbers vanilla itself uses. A player who has ever
 * opened a furnace knows this screen already, and there is nothing here worth teaching them twice.
 *
 * <p><b>It only exists because a MenuType is what binds a screen to a menu.</b> The menu subclasses
 * {@link net.minecraft.world.inventory.AbstractFurnaceMenu} and changes one method, so this could have
 * been vanilla's {@code FurnaceScreen} if that class were not typed to {@code FurnaceMenu}. That makes
 * it the thinnest of the mod's custom screens by a distance: no gauge vanilla lacks, no picker, no
 * extra slot - just the same furnace with a different menu behind it.
 */
public class SlagFurnaceScreen extends LayoutScreen<SlagFurnaceMenu> {

    /**
     * Vanilla's furnace layout, declared so the geometry sweeps can see it.
     *
     * <p>The slots are placed by {@code AbstractFurnaceMenu}'s constructor at these coordinates and not
     * from this declaration, which is the one place this screen departs from the framework's rule that
     * the layout is the single source. The alternative was reimplementing the menu to place its own
     * slots, which is what the Cupola had to do and what cost it the recipe book. Better to borrow the
     * menu and let {@code every_menu_slot_comes_from_its_layout} check the two agree.
     */
    public static final ScreenLayout LAYOUT = ScreenLayout.builder(GuiTheme.PANEL_W, GuiTheme.PANEL_H)
        .panel()
        .slot("input", 56, 17)
        .slot("fuel", 56, 53)
        .region("flame", 56, 36, GuiTheme.FLAME_W, GuiTheme.FLAME_H)
        .arrow("cook", 79, 34)
        .slot("result", 116, 35)
        .playerInventory(84)
        .build();

    public SlagFurnaceScreen(SlagFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, LAYOUT);
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
