package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.market.Market;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.ScreenLayout;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCMenus;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Buy Terminal's menu: the stock, and the balance to spend on it.
 *
 * <p><b>The stock is a list handed over when the screen opens</b>, read off the recipe manager's
 * {@code recompile:market_offer} recipes and written into the open buffer. The client draws that
 * list and sends back a row index; the server sells from its own copy of the same list. There is no
 * container here at all, only the player's inventory, so the sheet has somewhere to land.
 *
 * <p><b>A purchase either happens whole or not at all.</b> The balance is checked and taken before
 * the sheet is handed over, and a refused purchase takes nothing - which the paired test asserts,
 * because "no sheet appeared" passes just as well on a terminal that never works.
 */
public class BuyTerminalMenu extends AbstractContainerMenu {

    /**
     * How many offers the screen shows at once; the rest scroll.
     *
     * <p>Five, with the sixth row's space reserved for a "+N more (scroll)" line drawn at the
     * extrapolated cell under the last row - the connected-storage shelf's idiom. The first cut
     * showed six and nothing else, and ten offers shipped, so four of them were invisible with no
     * hint that a wheel would find them.
     */
    public static final int ROWS = 5;

    public static final ScreenLayout LAYOUT = ScreenLayout.builder(GuiTheme.PANEL_W, 230)
        .panel()
        // The hit region for scrolling is a backdrop so the rows may sit on it.
        .backdrop("stock", 8, 17, 160, 106)
        .rows("offers", ROWS, 8, 17, 160, 16, 18)
        .region("balance", 8, 126, 160, 9)
        .playerInventory(148)
        .build();

    /**
     * The player's own slots are the only ones here, so the ranges are named rather than typed
     * inline - the sibling Sell Terminal names its own, and two menus disagreeing about where the
     * hotbar starts is the class of bug the GUI framework exists to prevent.
     */
    private static final int BACKPACK_END = 27;
    private static final int HOTBAR_END = 36;

    private final List<Market.Offer> offers;
    private final ContainerLevelAccess access;
    private final BalanceSync balanceSync;

    /** Client factory with nothing on the shelf, for the geometry sweep. */
    public BuyTerminalMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL, List.of());
    }

    /** Client factory: the stock arrives in the open buffer. */
    public BuyTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, ContainerLevelAccess.NULL,
            Market.Offer.LIST_STREAM_CODEC.decode(buffer));
    }

    public BuyTerminalMenu(int containerId, Inventory inventory, ContainerLevelAccess access,
            List<Market.Offer> offers) {
        super(RCMenus.BUY_TERMINAL.get(), containerId);
        this.access = access;
        this.offers = List.copyOf(offers);

        LAYOUT.forEachPlayerSlot((index, x, y) -> this.addSlot(new Slot(inventory, index, x, y)));

        // Two slots, low half then high: a data slot is 16 bits on the wire and a balance is not.
        this.balanceSync = new BalanceSync(inventory.player);
        this.addDataSlot(this.balanceSync.lowSlot());
        this.addDataSlot(this.balanceSync.highSlot());
    }

    /** Everything for sale, in the order the screen draws it. */
    public List<Market.Offer> offers() {
        return offers;
    }

    /** What the screen shows: the attachment on the server, the synced halves on the client. */
    public int balance() {
        return this.balanceSync.balance();
    }

    /**
     * Buy one row. The id is the offer's index in {@link #offers()}, which both sides hold in the
     * same order because the server wrote it.
     */
    @Override
    public boolean clickMenuButton(Player buyer, int id) {
        if (id < 0 || id >= offers.size()) {
            return false;
        }
        Market.Offer offer = offers.get(id);
        if (!Market.debit(buyer, offer.price())) {
            return false;
        }
        // Into the inventory, and onto the floor if the inventory is full - the money is already
        // spent, and a sheet that vanished because the hotbar was full would be worse than one at
        // the player's feet.
        buyer.getInventory().placeItemBackInInventory(
            BlueprintItem.of(RCItems.BLUEPRINT.get(), offer.blueprint()));
        this.broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(Player user) {
        return stillValid(this.access, user, RCBlocks.BUY_TERMINAL.get());
    }

    /** Only the player's own slots exist, so shift-click moves between backpack and hotbar. */
    @Override
    public ItemStack quickMoveStack(Player mover, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < BACKPACK_END) {
            if (!this.moveItemStackTo(stack, BACKPACK_END, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, BACKPACK_END, false)) {
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
