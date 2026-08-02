package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.entity.HydroponicsBayBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Hydroponics Bay's menu (#43).
 *
 * <p><b>The fourth custom screen, and a recorded reversal.</b> CLAUDE.md's rule is that containers reuse
 * a vanilla screen and only producers with a gauge no vanilla screen has may have their own. A serious
 * alternative was considered and rejected here: reuse a chest screen for the two slots and let Jade carry
 * water, power and progress, the way it already does for the Tree Nursery and the generators. That would
 * have obeyed the rule without reversing it. The owner's call was a real GUI (2026-08-02), on the
 * grounds that this is the automation showpiece and three simultaneous resources on hover is worse than
 * three gauges in front of you.
 *
 * <p><b>Geometry lives here, not on the screen.</b> The Burner Generator shipped with its readout drawn
 * straight through its own fuel row, and nothing could have caught it because the coordinates were in a
 * client-only class. On the menu they are server-side, so {@code MenuLayoutTests} measures them.
 */
public class HydroponicsBayMenu extends AbstractContainerMenu {

    /** Crop, yield, byproduct. */
    public static final int SLOTS = 3;

    public static final int DATA_SIZE = 4;
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_GOAL = 1;
    public static final int DATA_WATER = 2;
    public static final int DATA_ENERGY = 3;

    /** Panel and slot geometry. Public so a GameTest can measure what the screen draws. */
    public static final int W = 176;
    public static final int H = 166;
    public static final int CELL = 18;
    public static final int INPUT_X = 44;
    public static final int INPUT_Y = 35;
    public static final int OUTPUT_X = 116;
    public static final int OUTPUT_Y = 26;
    /** The byproduct slot, stacked under the yield: seeds, and the occasional poisonous potato. */
    public static final int BYPRODUCT_X = 116;
    public static final int BYPRODUCT_Y = 44;
    public static final int INV_X = 8;
    public static final int INV_Y = 84;
    public static final int HOTBAR_Y = 142;
    /** The two vertical gauges, water on the left and power on the right of the machine slots. */
    public static final int WATER_X = 8;
    public static final int GAUGE_Y = 17;
    public static final int GAUGE_W = 14;
    public static final int GAUGE_H = 54;
    public static final int ENERGY_X = 154;
    /**
     * The grow arrow between input and output - vanilla's furnace arrow, so its 24x17 size is fixed by
     * the sprite rather than chosen. Centred in the gap between the two slots and on their row.
     */
    public static final int ARROW_X = 76;
    public static final int ARROW_Y = 35;
    public static final int ARROW_W = 24;
    public static final int ARROW_H = 17;

    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** Client constructor: an empty stand-in container, filled by the vanilla sync. */
    public HydroponicsBayMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(SLOTS), new SimpleContainerData(DATA_SIZE),
            ContainerLevelAccess.NULL);
    }

    public HydroponicsBayMenu(int containerId, Inventory inventory, Container container,
            ContainerData data, ContainerLevelAccess access) {
        super(RCMenus.HYDROPONICS_BAY.get(), containerId);
        this.container = container;
        this.data = data;
        this.access = access;
        checkContainerSize(container, SLOTS);
        checkContainerDataCount(data, DATA_SIZE);

        // The crop slot. One item, and that one grows forever until the player takes it back out - so
        // it holds a single plant rather than a stack waiting to be fed in.
        addSlot(new Slot(container, HydroponicsBayBlockEntity.SLOT_INPUT, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return HydroponicsBayBlockEntity.isGrowable(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        // Both harvest slots are take-only. Without this a player could park anything in one and stall
        // the machine, which is the same brick the Cupola's input guard exists to prevent.
        addSlot(new TakeOnly(container, HydroponicsBayBlockEntity.SLOT_OUTPUT, OUTPUT_X, OUTPUT_Y));
        addSlot(new TakeOnly(container, HydroponicsBayBlockEntity.SLOT_BYPRODUCT,
            BYPRODUCT_X, BYPRODUCT_Y));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9,
                    INV_X + col * CELL, INV_Y + row * CELL));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INV_X + col * CELL, HOTBAR_Y));
        }
        addDataSlots(data);
    }

    public int progress() {
        return data.get(DATA_PROGRESS);
    }

    public int goal() {
        return data.get(DATA_GOAL);
    }

    public int water() {
        return data.get(DATA_WATER);
    }

    public int energy() {
        return data.get(DATA_ENERGY);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int inventoryStart = SLOTS;
        if (index < inventoryStart) {
            // Machine to player.
            if (!moveItemStackTo(stack, inventoryStart, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, HydroponicsBayBlockEntity.SLOT_INPUT,
                HydroponicsBayBlockEntity.SLOT_INPUT + 1, false)) {
            // Player to machine, input only - shift-clicking a stack into the OUTPUT slot would jam it.
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    /** A harvest slot: the machine puts things in, the player takes them out. */
    private static final class TakeOnly extends Slot {
        TakeOnly(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, RCBlocks.HYDROPONICS_BAY.get());
    }
}
