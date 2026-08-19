package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.entity.CupolaFurnaceBlockEntity;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.ScreenLayout;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Cupola Furnace's menu: a furnace with a second output, for the slag (#236).
 *
 * <p><b>Vanilla's furnace menu cannot be subclassed into this.</b> {@code AbstractFurnaceMenu}'s
 * constructor calls {@code checkContainerSize(container, 3)} and then hard-codes three
 * {@code addSlot} calls at vanilla's coordinates, so a fourth slot is not a parameter it takes - it
 * throws on construction. This is the same wall {@code ScrapCraftingStationMenu} hit, where
 * {@code CraftingMenu} hard-locks itself to {@code MenuType.CRAFTING}, and the answer is the same:
 * reimplement the small amount of menu this machine needs over {@link AbstractContainerMenu}.
 *
 * <p><b>This is the mod's fifth custom screen and a recorded exception</b>, per the rule that every one
 * of them is a deliberate call written down rather than a habit. The reason it earns one: the byproduct
 * slot has no vanilla equivalent to borrow. Every vanilla cooking screen has exactly one output because
 * every vanilla cooking recipe has exactly one result - a furnace that hands back two things is outside
 * what the vanilla screens were built to show. The alternative was leaving slag to pop onto the floor,
 * which is what shipped first and what the owner rejected (2026-08-18): a machine that litters is not a
 * machine with an output.
 *
 * <p>The data is vanilla's own furnace {@code ContainerData} - lit time, lit duration, cooking progress,
 * cooking total - so the flame and the arrow behave exactly as a player expects. Nothing here reinvents
 * how a furnace looks; the only new thing on the screen is the extra slot.
 */
public class CupolaFurnaceMenu extends AbstractContainerMenu {

    /** Vanilla's furnace layout plus one: 0 input, 1 fuel, 2 result, 3 slag. */
    public static final int SLOTS = CupolaFurnaceBlockEntity.SLOTS;
    /** Vanilla's four furnace values: lit, lit total, cooking, cooking total. */
    public static final int DATA_SIZE = 4;

    /**
     * The screen's layout, declared once and read by both halves.
     *
     * <p>Vanilla's furnace geometry for the three slots a player already knows - input at (56,17), fuel
     * at (56,53), result at (116,35) - so the machine reads as a furnace at a glance and only the new
     * thing is new. The slag sits beside the result rather than under it: a byproduct is a second
     * output, not a lesser one, and stacking them would have put it where vanilla's screens put
     * nothing at all.
     */
    public static final ScreenLayout LAYOUT = ScreenLayout.builder(GuiTheme.PANEL_W, GuiTheme.PANEL_H)
        .panel()
        .slot("input", 56, 17)
        .slot("fuel", 56, 53)
        .well("flame", 56, 36, 14, 14)
        .arrow("cook", 79, 34)
        .slot("result", 116, 35)
        .slot("slag", 140, 35)
        .playerInventory(84)
        .build();

    private static final int INV_START = SLOTS;
    private static final int INV_MAIN_END = INV_START + 27;
    private static final int INV_END = INV_START + 36;

    private final Container container;
    private final ContainerData data;

    /** Client factory: a dummy container and data, filled by the sync (see {@code RCMenus}). */
    public CupolaFurnaceMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(SLOTS), new SimpleContainerData(DATA_SIZE));
    }

    public CupolaFurnaceMenu(int containerId, Inventory inventory, Container container,
            ContainerData data) {
        super(RCMenus.CUPOLA_FURNACE.get(), containerId);
        checkContainerSize(container, SLOTS);
        checkContainerDataCount(data, DATA_SIZE);
        this.container = container;
        this.data = data;

        // Placed FROM the layout rather than from numbers of this class's own, which is the whole
        // point of the framework: the drawing and the hit boxes cannot disagree if there is only one of
        // them to read.
        LAYOUT.forEachSlot("input", (i, x, y) -> this.addSlot(new Slot(container, 0, x, y)));
        // A plain Slot gated on the container, not vanilla's FurnaceFuelSlot - that class takes an
        // AbstractFurnaceMenu and this deliberately is not one. The container's own canPlaceItem is
        // where the fuel rule lives anyway, so asking it is both shorter and the single source.
        LAYOUT.forEachSlot("fuel", (i, x, y) -> this.addSlot(new Slot(container, 1, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(1, stack);
            }
        }));
        LAYOUT.forEachSlot("result", (i, x, y) ->
            this.addSlot(new FurnaceResultSlot(inventory.player, container, 2, x, y)));
        // A RESULT SLOT, not a plain one: the slag is output, so nothing may be put INTO it by hand.
        // Without that a player can park anything there and the machine stalls, because the ticker
        // holds when the slag slot is full - which reads as a broken furnace with nothing to explain it.
        LAYOUT.forEachSlot("slag", (i, x, y) ->
            this.addSlot(new FurnaceResultSlot(inventory.player, container, 3, x, y)));
        LAYOUT.forEachPlayerSlot((index, x, y) -> this.addSlot(new Slot(inventory, index, x, y)));
        this.addDataSlots(data);
    }

    /** Fraction of the current cook that is done, for the arrow. */
    public float cookProgress() {
        int total = this.data.get(3);
        int done = this.data.get(2);
        return total == 0 || done == 0 ? 0.0F : (float) done / total;
    }

    /** Fraction of the current fuel that is left, for the flame. */
    public float burnProgress() {
        int total = this.data.get(1);
        return total == 0 ? 0.0F : (float) this.data.get(0) / total;
    }

    public boolean isLit() {
        return this.data.get(0) > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /**
     * Shift-click routing, and the slag slot is the reason it is written out rather than borrowed.
     *
     * <p>Both outputs move OUT to the player and nothing may be shifted INTO either. Fuel goes to the
     * fuel slot, and anything else goes to the input - which is vanilla's behaviour with one extra
     * output to account for.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 2 || index == 3) {
            if (!this.moveItemStackTo(stack, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, original);
        } else if (index >= INV_START) {
            if (this.container.canPlaceItem(1, stack)
                    && this.moveItemStackTo(stack, 1, 2, false)) {
                // fuel
            } else if (this.moveItemStackTo(stack, 0, 1, false)) {
                // input
            } else if (index < INV_MAIN_END) {
                if (!this.moveItemStackTo(stack, INV_MAIN_END, INV_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, INV_START, INV_MAIN_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, INV_START, INV_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    /** Only useful to a test: which block this menu belongs to. */
    public static net.minecraft.world.level.block.Block block() {
        return RCBlocks.CUPOLA_FURNACE.get();
    }
}
