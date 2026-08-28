package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.entity.SequencerBlockEntity;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.ScreenLayout;
import com.flatts.recompile.registry.RCMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * The Sequencer's menu (#294).
 *
 * <p><b>The layout is declared once, here, in common code.</b> That is the framework's whole point
 * (#164): slot coordinates used to be baked into {@code Slot} objects in the menu while the drawing
 * that had to line up with them lived in a client-only class, with nothing connecting the two. A
 * server-side test can measure this; it could never be asked about a client class.
 *
 * <p>Vanilla's furnace geometry, so a meter, two slots and an arrow fit without landing on each other.
 */
public class SequencerMenu extends AbstractContainerMenu {

    public static final int DATA_SIZE = 2;

    public static final ScreenLayout LAYOUT = ScreenLayout.builder(GuiTheme.PANEL_W, GuiTheme.PANEL_H)
        .panel()
        .well("meter", 8, 17, 14, 54)
        .slotRow("amber", 1, 56, 35)
        .arrow("progress", 79, 34)
        .slotRow("fragment", 1, 116, 35)
        .playerInventory(84)
        .build();

    private static final int INV_START = SequencerBlockEntity.SLOT_COUNT;
    private static final int INV_MAIN_END = INV_START + 27;
    private static final int INV_END = INV_START + 36;

    private final Container container;
    private final ContainerData data;

    /** Client factory: dummy container + data, filled by the sync (see {@code RCMenus}). */
    public SequencerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(SequencerBlockEntity.SLOT_COUNT),
            new SimpleContainerData(DATA_SIZE));
    }

    public SequencerMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(RCMenus.SEQUENCER.get(), containerId);
        checkContainerSize(container, SequencerBlockEntity.SLOT_COUNT);
        this.container = container;
        this.data = data;

        // mayPlace defers to the container, so "stamped amber only" lives in exactly one place and a
        // player and a hopper cannot disagree about it.
        LAYOUT.forEachSlot("amber", (index, x, y) -> this.addSlot(new Slot(container, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(SequencerBlockEntity.INPUT_SLOT, stack);
            }
        }));
        LAYOUT.forEachSlot("fragment", (index, x, y) ->
            this.addSlot(new Slot(container, SequencerBlockEntity.OUTPUT_SLOT, x, y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;   // output only; nothing goes in here by hand
                }
            }));
        LAYOUT.forEachPlayerSlot((index, x, y) -> this.addSlot(new Slot(inventory, index, x, y)));
        this.addDataSlots(data);
    }

    public int energy() {
        return this.data.get(0);
    }

    public int energyCapacity() {
        return SequencerBlockEntity.CAPACITY;
    }

    /** Ticks into the current read. The arrow does its own proportion, the way vanilla's does. */
    public int progressTicks() {
        return this.data.get(1);
    }

    public int ticksPerRead() {
        return SequencerBlockEntity.TICKS_PER_READ;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /**
     * Shift-click. Amber goes to the input and anything else is refused, so shift-clicking a stack of
     * scrap at the machine does nothing rather than silently jamming a slot.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
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
        } else if (SequencerBlockEntity.canSequence(stack)) {
            if (!this.moveItemStackTo(stack, SequencerBlockEntity.INPUT_SLOT,
                    SequencerBlockEntity.INPUT_SLOT + 1, false)) {
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
