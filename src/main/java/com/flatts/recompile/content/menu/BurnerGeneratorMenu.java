package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.entity.BurnerGeneratorBlockEntity;
import com.flatts.recompile.registry.RCMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Burner Generator's menu (#72): five fuel slots and a power meter.
 *
 * <p>This block started with no screen at all, then borrowed vanilla's {@link net.minecraft.world.inventory.HopperMenu}
 * when it gained a fuel buffer. Neither could show <b>energy</b>, and a generator whose stored power is
 * invisible is a machine you cannot reason about - so this is a bespoke menu carrying a
 * {@link ContainerData} for the buffer and the burn (owner call, 2026-07-31).
 *
 * <p>That makes it the mod's <b>third</b> custom screen, after the Scrap Crafting Table and the Tree
 * Nursery. The rule it bends - "reuse a vanilla screen, never mint one" - holds for containers; no vanilla
 * screen has an energy bar, so a machine with energy has nothing to reuse.
 */
public class BurnerGeneratorMenu extends AbstractContainerMenu {

    public static final int FUEL_SLOTS = BurnerGeneratorBlockEntity.FUEL_SLOTS;
    /** {@code [0]} stored FE, {@code [1]} ticks of burn left. Capacity is a constant, so it is not synced. */
    public static final int DATA_SIZE = 2;

    /**
     * The screen's layout, owned here rather than in the client class.
     *
     * <p>The menu places slots and the screen draws them, so duplicating these was two copies of one
     * truth - and they drifted immediately: the first version drew the readout at x=34 while the fuel row
     * started at x=43, so the numbers ran straight through the slots. Sharing them lets a server-side
     * test check the layout, which a client-only class could never be asked about.
     *
     * <p>Vanilla's furnace geometry (176x166), so there is room for a meter, a fuel row, a readout and
     * the player inventory without any of them landing on each other.
     */
    public static final int W = 176;
    public static final int H = 166;
    public static final int CELL = 18;
    public static final int FUEL_X = 43;
    public static final int FUEL_Y = 30;
    public static final int INV_X = 8;
    public static final int INV_Y = 84;
    public static final int HOTBAR_Y = 142;
    public static final int METER_X = 8;
    public static final int METER_Y = 17;
    public static final int METER_W = 14;
    public static final int METER_H = 54;
    public static final int READOUT_X = FUEL_X;
    public static final int READOUT_Y = 56;
    /** Font line height, and a generous width for "20,000 / 20,000 FE". */
    public static final int READOUT_H = 9;
    public static final int READOUT_W = 120;

    private static final int INV_START = FUEL_SLOTS;
    private static final int INV_MAIN_END = INV_START + 27;
    private static final int INV_END = INV_START + 36;

    private final Container container;
    private final ContainerData data;

    /** Client factory: dummy container + data, filled by the sync (see {@code RCMenus}). */
    public BurnerGeneratorMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(FUEL_SLOTS), new SimpleContainerData(DATA_SIZE));
    }

    public BurnerGeneratorMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(RCMenus.BURNER_GENERATOR.get(), containerId);
        checkContainerSize(container, FUEL_SLOTS);
        this.container = container;
        this.data = data;

        // One row of fuel, centred. mayPlace defers to the container so the "only fuel" rule lives in
        // exactly one place and a pipe and a player cannot disagree about it.
        for (int i = 0; i < FUEL_SLOTS; i++) {
            this.addSlot(new Slot(container, i, FUEL_X + i * CELL, FUEL_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return container.canPlaceItem(this.getContainerSlot(), stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, INV_X + col * CELL, INV_Y + row * CELL));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, INV_X + col * CELL, HOTBAR_Y));
        }
        this.addDataSlots(data);
    }

    public int energy() {
        return this.data.get(0);
    }

    public int energyCapacity() {
        return BurnerGeneratorBlockEntity.CAPACITY;
    }

    public boolean isLit() {
        return this.data.get(1) > 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /**
     * Shift-click. Fuel goes to the buffer and anything else is refused, so shift-clicking a stack of
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
        } else if (!this.moveItemStackTo(stack, 0, INV_START, false)) {
            // Not fuel, so fall through to the usual backpack <-> hotbar shuffle.
            if (index < INV_MAIN_END) {
                if (!this.moveItemStackTo(stack, INV_MAIN_END, INV_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, INV_START, INV_MAIN_END, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
