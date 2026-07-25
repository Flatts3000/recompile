package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.ScrapNetwork;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The Scrap Crafting Table's menu: vanilla 3x3 crafting, revalidated against the scrap table, plus
 * <b>craft-from-storage</b> (design P2.10 flow 4, the Tinkers' Crafting Station pattern). Shift-click
 * the result and the grid restocks from the connected scrap network between crafts, so one action
 * crafts a whole run straight out of your bins.
 *
 * <p>This is the server-side functional core. It reuses vanilla {@link CraftingMenu} wholesale (the
 * client still builds a stock {@code CraftingMenu} + screen under {@code MenuType.CRAFTING}, so no
 * registration is needed, exactly as the old {@code ScrapCraftingMenu} did); the only addition is the
 * refill, which fills the grid through the public slot list, letting {@code TransientCraftingContainer}
 * recompute the result for free. The connected-storage <b>panel</b> is a separate step (it needs a
 * custom menu type + screen); this piece makes the crafting itself pull from the network.
 */
public class ScrapCraftingStationMenu extends CraftingMenu {

    /** Grid slots are 1..9 in {@link CraftingMenu} (slot 0 is the result). */
    private static final int GRID_START = 1;
    private static final int GRID_SIZE = 9;

    private final ContainerLevelAccess access;
    private final Level level;
    private final BlockPos pos;

    public ScrapCraftingStationMenu(int containerId, Inventory inventory,
            ContainerLevelAccess access, Level level, BlockPos pos) {
        super(containerId, inventory, access);
        this.access = access;
        this.level = level;
        this.pos = pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, RCBlocks.SCRAP_CRAFTING_TABLE.get());
    }

    /**
     * Shift-clicking the result crafts, then refills the emptied grid slots from the connected network.
     * The vanilla quick-move loop ({@code AbstractContainerMenu.doClick}) keeps calling this while the
     * result slot still holds the same item, so a restocked grid means the whole run crafts in one
     * click - the craft-from-storage payoff. Every other slot behaves exactly like vanilla.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index != RESULT_SLOT || level.isClientSide()) {
            return super.quickMoveStack(player, index);
        }
        Item[] pattern = capturePattern();
        ItemStack moved = super.quickMoveStack(player, index);   // vanilla craft + move + grid decrement
        refillGrid(player, pattern);
        return moved;
    }

    /** The item in each grid slot, so an emptied slot can be restocked with the same one. Test seam. */
    public Item[] capturePattern() {
        Item[] pattern = new Item[GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack stack = this.slots.get(GRID_START + i).getItem();
            pattern[i] = stack.isEmpty() ? Items.AIR : stack.getItem();
        }
        return pattern;
    }

    /** Restock each emptied grid slot with its pattern item, from bins -> barrel -> inventory. Test seam. */
    public void refillGrid(Player player, Item[] pattern) {
        List<BlockPos> members = ScrapNetwork.collect(level, pos);
        List<ScrapBinBlockEntity> bins = ScrapNetwork.bins(level, members);
        List<Container> barrels = ScrapNetwork.barrels(level, members);
        for (int i = 0; i < GRID_SIZE; i++) {
            Slot slot = this.slots.get(GRID_START + i);
            Item want = pattern[i];
            if (want == Items.AIR || !slot.getItem().isEmpty()) {
                continue;
            }
            ItemStack pulled = pullOne(want, bins, barrels, player);
            if (!pulled.isEmpty()) {
                slot.set(pulled);   // notifies TransientCraftingContainer -> result recomputes
            }
        }
    }

    /** One {@code want} from a bound bin, else a barrel, else the player's inventory; empty if none. */
    private ItemStack pullOne(Item want, List<ScrapBinBlockEntity> bins, List<Container> barrels, Player player) {
        for (ScrapBinBlockEntity bin : bins) {
            if (bin.boundMaterial() == want && bin.amount() > 0) {
                ItemStack out = bin.withdraw(true);
                if (!out.isEmpty()) {
                    return out;
                }
            }
        }
        for (Container barrel : barrels) {
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                ItemStack stack = barrel.getItem(slot);
                if (stack.is(want)) {
                    ItemStack out = stack.split(1);
                    barrel.setChanged();
                    return out;
                }
            }
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(want)) {
                return stack.split(1);
            }
        }
        return ItemStack.EMPTY;
    }
}
