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
import net.minecraft.world.inventory.DataSlot;
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

    /** How many offers the screen shows at once; the rest scroll. */
    public static final int ROWS = 6;

    public static final ScreenLayout LAYOUT = ScreenLayout.builder(GuiTheme.PANEL_W, 230)
        .panel()
        // The hit region for scrolling is a backdrop so the rows may sit on it.
        .backdrop("stock", 8, 17, 160, 106)
        .rows("offers", ROWS, 8, 17, 160, 16, 18)
        .region("balance", 8, 126, 160, 9)
        .playerInventory(148)
        .build();

    private final List<Market.Offer> offers;
    private final ContainerLevelAccess access;
    private final Player player;

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
        this.player = inventory.player;
        this.offers = List.copyOf(offers);

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

    /** Everything for sale, in the order the screen draws it. */
    public List<Market.Offer> offers() {
        return offers;
    }

    public int balance() {
        return Market.balance(player);
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
        if (index < 27) {
            if (!this.moveItemStackTo(stack, 27, 36, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, 27, false)) {
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
