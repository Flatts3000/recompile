package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.market.Market;
import com.flatts.recompile.content.menu.BuyTerminalMenu;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Buy Terminal's screen: the stock as rows of sheet, name and price, and the balance under it.
 *
 * <p>The tenth custom screen. A row you can afford is priced in green and one you cannot in red, so
 * "why will it not sell me this" is answered before the click rather than by a click that does
 * nothing. Clicking a row sends its index; the server holds the same list in the same order.
 */
public class BuyTerminalScreen extends LayoutScreen<BuyTerminalMenu> {

    private int scroll;

    public BuyTerminalScreen(BuyTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BuyTerminalMenu.LAYOUT);
    }

    @Override
    protected void paint(GuiPainter painter, int mouseX, int mouseY) {
        List<Market.Offer> offers = this.menu.offers();
        int balance = this.menu.balance();
        int shown = shown(offers.size());

        if (offers.isEmpty()) {
            painter.wrapped("offers", Component.translatable("container.recompile.no_offers"),
                GuiTheme.TEXT_MUTED);
        }
        int hovered = painter.overIndex("offers", shown, mouseX, mouseY);
        Font font = painter.font();
        for (int row = 0; row < shown; row++) {
            Market.Offer offer = offers.get(scroll + row);
            if (row == hovered) {
                painter.tintPadded("offers", row, 1, GuiTheme.HOVER_ROW);
            }
            painter.item("offers", row, BlueprintItem.of(RCItems.BLUEPRINT.get(), offer.blueprint()));
            // The price is right-aligned to the row's edge and the name gets whatever is left, cut
            // with an ellipsis. "Netherite Upgrade Pattern" at "1,500 scrip" ran through the price
            // and out of the panel when both were placed at fixed columns; the unit lives on the
            // balance line and the hover tooltip, so the column is the bare number.
            int width = painter.at("offers", row).width();
            String price = String.format("%,d", offer.price());
            int priceWidth = font.width(price);
            painter.textIn("offers", row, width - priceWidth, 4, price,
                offer.price() <= balance ? GuiTheme.TEXT_GOOD : GuiTheme.TEXT_WARN);
            painter.textIn("offers", row, NAME_X, 4,
                fit(font, BlueprintItem.setName(offer.blueprint()).getString(),
                    width - NAME_X - priceWidth - GAP),
                GuiTheme.TEXT_LABEL);
        }
        // The tail line sits in the extrapolated cell under the last row, which a single-column
        // run answers for on purpose - see ScreenLayout.Group.cell.
        int hidden = offers.size() - scroll - shown;
        if (hidden > 0) {
            painter.textIn("offers", shown, 20, 4,
                Component.translatable("container.recompile.more_scroll", hidden).getString(),
                GuiTheme.TEXT_MUTED);
        } else if (scroll > 0) {
            painter.textIn("offers", shown, 20, 4,
                Component.translatable("container.recompile.scroll_up").getString(),
                GuiTheme.TEXT_MUTED);
        }

        painter.text("balance", Component.translatable("container.recompile.scrip_balance",
            String.format("%,d", balance)).getString(), GuiTheme.TEXT_LABEL);
    }

    /** Where a row's name starts: past the sheet icon. */
    private static final int NAME_X = 20;
    /** Air between the end of a name and the start of its price. */
    private static final int GAP = 6;
    private static final String ELLIPSIS = "...";

    /** A string cut to a width, with an ellipsis if anything was cut. */
    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ELLIPSIS))).stripTrailing()
            + ELLIPSIS;
    }

    private int shown(int total) {
        return Math.max(0, Math.min(BuyTerminalMenu.ROWS, total - scroll));
    }

    /**
     * Hover a row for the whole story: the full name, the price with its unit, and how short you
     * are if you cannot afford it. The row itself is cut to fit; this is where nothing is.
     */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Market.Offer> offers = this.menu.offers();
        int row = overIndex("offers", shown(offers.size()), mouseX, mouseY);
        if (row < 0) {
            return;
        }
        Market.Offer offer = offers.get(scroll + row);
        List<Component> lines = new ArrayList<>();
        lines.add(BlueprintItem.setName(offer.blueprint()));
        lines.add(Component.translatable("container.recompile.offer_price",
            String.format("%,d", offer.price())).withStyle(ChatFormatting.GRAY));
        int shortBy = offer.price() - this.menu.balance();
        if (shortBy > 0) {
            lines.add(Component.translatable("tooltip.recompile.market_short",
                String.format("%,d", shortBy)).withStyle(ChatFormatting.RED));
        }
        graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isOver("stock", mouseX, mouseY)) {
            int max = Math.max(0, this.menu.offers().size() - BuyTerminalMenu.ROWS);
            this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            int row = overIndex("offers", shown(this.menu.offers().size()), event.x(), event.y());
            if (row >= 0) {
                // The vanilla Stonecutter/Loom path: the id travels as a VAR_INT, no custom packet.
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                    scroll + row);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
