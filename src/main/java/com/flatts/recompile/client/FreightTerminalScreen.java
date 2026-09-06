package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.freight.FreightManifest;
import com.flatts.recompile.content.menu.FreightTerminalMenu;
import com.flatts.recompile.gui.GuiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Freight Terminal's screen: the manifest, and how much of it has gone (#387).
 *
 * <p>The twelfth custom screen. It exists because no vanilla screen shows a delivery manifest, and a
 * chest screen would show the goods while hiding the only numbers that matter.
 *
 * <p><b>A satisfied line is coloured, not removed.</b> Watching lines go green is the readout; a line
 * that vanished when it completed would leave the player unable to tell a finished requirement from
 * one that was never on the list.
 */
public class FreightTerminalScreen extends LayoutScreen<FreightTerminalMenu> {

    /** Room for the item icon before the name starts. */
    private static final int NAME_X = 20;

    private static final int GAP = 4;

    /** The tier the manifest was drawn for. See {@link #paint}. */
    private final int openedAtTier;

    public FreightTerminalScreen(FreightTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, FreightTerminalMenu.LAYOUT);
        this.openedAtTier = menu.manifest().tier();
    }

    @Override
    protected void paint(GuiPainter painter, int mouseX, int mouseY) {
        FreightManifest manifest = this.menu.manifest();

        // THE MANIFEST IS SENT ONCE, IN THE OPEN BUFFER, AND THE NUMBERS BESIDE IT ARE LIVE.
        // Completing a phase with the screen open is the ordinary case - you are watching the last
        // line fill - and after it the server resolves delivered-by-index against the NEW phase while
        // the client still holds the old one's names and quotas. Lines would render green for goods
        // that are no longer wanted, and the strip would refuse what the screen still listed. The
        // tier is synced for exactly this; say so and stop drawing rather than draw a lie.
        if (this.menu.tier() != openedAtTier) {
            painter.wrapped("manifest",
                Component.translatable("container.recompile.freight.phase_moved"),
                GuiTheme.TEXT_GOOD);
            return;
        }

        if (manifest.isEmpty()) {
            // The ladder is finished, or a pack shipped no phases. Both are real states and neither
            // is an empty table.
            painter.wrapped("manifest",
                Component.translatable("container.recompile.freight.nothing_wanted"),
                GuiTheme.TEXT_MUTED);
            return;
        }

        Font font = painter.font();
        // Which rung this is, above the lines. Without it the screen shows a list of goods with no
        // indication that a ladder exists at all.
        painter.text("phase", Component.translatable("container.recompile.freight.phase_of",
            Component.translatable(manifest.name()),
            manifest.tier() + 1, manifest.ladderLength()).getString(), GuiTheme.TEXT_LABEL);

        for (int row = 0; row < manifest.lines().size(); row++) {
            FreightManifest.Line line = manifest.lines().get(row);
            int delivered = Math.min(this.menu.delivered(row), line.required());
            boolean done = delivered >= line.required();

            painter.item("manifest", row, new ItemStack(line.item()));

            // The count is right-aligned and the name takes what is left, cut with an ellipsis. The
            // Buy Terminal learned this the hard way: fixed columns ran a long name through the
            // number and out of the panel.
            int width = painter.at("manifest", row).width();
            String count = String.format("%,d / %,d", delivered, line.required());
            int countWidth = font.width(count);
            painter.textIn("manifest", row, width - countWidth, 2, count,
                done ? GuiTheme.TEXT_GOOD : GuiTheme.TEXT_LABEL);
            painter.textIn("manifest", row, NAME_X, 2,
                fit(font, new ItemStack(line.item()).getHoverName().getString(),
                    width - NAME_X - countWidth - GAP),
                done ? GuiTheme.TEXT_GOOD : GuiTheme.TEXT_LABEL);
        }
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
