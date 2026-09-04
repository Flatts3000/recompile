package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.market.Market;
import com.flatts.recompile.content.menu.BuyTerminalMenu;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
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
        for (int row = 0; row < shown; row++) {
            Market.Offer offer = offers.get(scroll + row);
            if (row == hovered) {
                painter.tintPadded("offers", row, 1, GuiTheme.HOVER_ROW);
            }
            painter.item("offers", row, BlueprintItem.of(RCItems.BLUEPRINT.get(), offer.blueprint()));
            painter.textIn("offers", row, 20, 4, BlueprintItem.setName(offer.blueprint()).getString(),
                GuiTheme.TEXT_LABEL);
            painter.textIn("offers", row, 110, 4,
                Component.translatable("container.recompile.offer_price",
                    String.format("%,d", offer.price())).getString(),
                offer.price() <= balance ? GuiTheme.TEXT_GOOD : GuiTheme.TEXT_WARN);
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

    private int shown(int total) {
        return Math.max(0, Math.min(BuyTerminalMenu.ROWS, total - scroll));
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
