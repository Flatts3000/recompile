package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.market.Market;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.ScreenLayout;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCMenus;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Sell Terminal's menu: a grid of goods, a quote, a Sell button and the balance.
 *
 * <p><b>The goods grid is menu-local, like a crafting grid.</b> It belongs to this open screen and to
 * nobody else: closing the screen hands everything back ({@link #removed}), and nothing in the world
 * can see it. That is what makes the block a terminal rather than a container - there is no block
 * entity for a hopper to find - and it is why the spec's "selling cannot be automated" needed no
 * rule to be true.
 *
 * <p><b>The quote is computed on both sides from the same synced data</b> (the tag and the data map
 * are both synced), so the screen shows what the sale will pay before the player commits, and the
 * server charges the same number when they do. {@code mayPlace} refuses anything unsellable at the
 * slot, so a player learns an item has no price by it not going in rather than by selling it for
 * nothing.
 *
 * <p>The balance rides a {@link DataSlot} that reads the player's attachment on the server and mirrors
 * the synced value into the client player's copy, the way every other screen here moves a number.
 */
public class SellTerminalMenu extends AbstractContainerMenu {

    /** The button id the screen sends to sell everything in the grid. */
    public static final int SELL_BUTTON = 0;

    public static final int GOODS_SLOTS = 9;

    public static final ScreenLayout LAYOUT = ScreenLayout.builder(GuiTheme.PANEL_W, 184)
        .panel()
        .slotGrid("goods", 3, 3, 8, 17)
        // The quote sits beside the grid, not under it: "what will this pay" is answered next to the
        // things being asked about.
        .region("quote", 70, 17, 98, 27)
        // The button is a backdrop with its label as a region on top, which is the only way a
        // labelled surface passes the overlap sweep; the sweep is right that a label over a plain
        // region would be a bug, and a backdrop is the declared exception for exactly this.
        .backdrop("sell", 70, 48, 60, 18)
        .region("sell_label", 76, 53, 48, 9)
        .region("balance", 8, 76, 160, 9)
        .playerInventory(102)
        .build();

    private static final int INV_START = GOODS_SLOTS;
    private static final int INV_MAIN_END = INV_START + 27;
    private static final int INV_END = INV_START + 36;

    private final SimpleContainer goods = new SimpleContainer(GOODS_SLOTS);
    private final ContainerLevelAccess access;
    private final Player player;

    /** Client factory: no block to stand at, the balance arrives through the data slot. */
    public SellTerminalMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public SellTerminalMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(RCMenus.SELL_TERMINAL.get(), containerId);
        this.access = access;
        this.player = inventory.player;

        LAYOUT.forEachSlot("goods", (index, x, y) -> this.addSlot(new Slot(goods, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return Market.isSellable(stack);
            }
        }));
        LAYOUT.forEachPlayerSlot((index, x, y) -> this.addSlot(new Slot(inventory, index, x, y)));

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return Market.balance(player);
            }

            @Override
            public void set(int value) {
                Market.setBalance(player, value);
            }
        });
    }

    /** The goods waiting to be sold. Exposed for the tests, which fill it directly. */
    public SimpleContainer goods() {
        return goods;
    }

    /** What the grid would pay right now. */
    public int quote() {
        return Market.quote(goods);
    }

    public int balance() {
        return Market.balance(player);
    }

    /**
     * The sale. Everything in the grid goes and the balance goes up by the quote; an empty grid is a
     * refused click rather than a zero-scrip sale.
     */
    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (id != SELL_BUTTON) {
            return false;
        }
        int total = Market.quote(goods);
        if (total <= 0) {
            return false;
        }
        goods.clearContent();
        Market.credit(clicker, total);
        this.broadcastChanges();
        return true;
    }

    @Override
    public void removed(Player closer) {
        super.removed(closer);
        this.clearContainer(closer, goods);
    }

    @Override
    public boolean stillValid(Player user) {
        return stillValid(this.access, user, RCBlocks.SELL_TERMINAL.get());
    }

    /** Shift-click: sellable goods go to the grid, everything else is refused, grid empties home. */
    @Override
    public ItemStack quickMoveStack(Player mover, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INV_START) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (Market.isSellable(stack)) {
            if (!this.moveItemStackTo(stack, 0, GOODS_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < INV_MAIN_END) {
            if (!this.moveItemStackTo(stack, INV_MAIN_END, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INV_START, INV_MAIN_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
