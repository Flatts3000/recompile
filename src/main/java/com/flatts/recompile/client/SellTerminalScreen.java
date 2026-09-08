package com.flatts.recompile.client;

import com.flatts.recompile.client.gui.GuiPainter;
import com.flatts.recompile.client.gui.LayoutScreen;
import com.flatts.recompile.content.menu.SellTerminalMenu;
import com.flatts.recompile.gui.GuiTheme;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Sell Terminal's screen: goods on the left, what they pay on the right, and the balance.
 *
 * <p>The ninth custom screen in this mod, and the reason it exists is the one line of text in the
 * middle: <b>no vanilla screen shows a price.</b> A chest screen would show the goods and hide the
 * only number that matters, and a shop that tells you the price after you have handed the goods over
 * is a con. The quote is drawn before the sale from the same synced tables the server charges from.
 */
public class SellTerminalScreen extends LayoutScreen<SellTerminalMenu> {

    public SellTerminalScreen(SellTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SellTerminalMenu.LAYOUT);
    }

    @Override
    protected void paint(GuiPainter painter, int mouseX, int mouseY) {
        int quote = this.menu.quote();
        if (quote > 0) {
            painter.wrapped("quote", Component.translatable("container.recompile.sell_quote",
                String.format("%,d", quote)), GuiTheme.TEXT_LABEL);
        } else {
            painter.wrapped("quote", Component.translatable("container.recompile.sell_quote_empty"),
                GuiTheme.TEXT_MUTED);
        }

        // The button reads as pressable only when there is something to sell.
        boolean live = quote > 0;
        painter.slab("sell", live && painter.isOver("sell", mouseX, mouseY)
            ? GuiTheme.SELECT_TINT : GuiTheme.SIDE_PANEL_BODY);
        painter.text("sell_label", Component.translatable("container.recompile.sell_button").getString(),
            live ? GuiTheme.TEXT_BRIGHT : GuiTheme.TEXT_DIM);

        painter.text("balance", Component.translatable("container.recompile.scrip_balance",
            String.format("%,d", this.menu.balance())).getString(), GuiTheme.TEXT_LABEL);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isOver("sell", event.x(), event.y())) {
            // A DEAD BUTTON IS NOT SENT. paint() already draws it dim and refuses it the hover tint
            // when there is nothing to sell, and it was still clickable - so the one control on this
            // screen looked disabled, sent anyway, and got silence back from a server that had
            // nothing to sell either. The sibling Buy Terminal refuses a locked row before sending it
            // for exactly this reason; this is that rule applied to the other terminal.
            if (this.menu.quote() <= 0) {
                return true;
            }
            press(SellTerminalMenu.SELL_BUTTON);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
