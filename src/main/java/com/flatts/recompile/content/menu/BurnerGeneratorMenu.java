package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.entity.BurnerGeneratorBlockEntity;
import com.flatts.recompile.gui.GuiTheme;
import com.flatts.recompile.gui.ScreenLayout;
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
     * started at x=43, so the numbers ran straight through the slots. Declaring them once lets a
     * server-side test check the layout, which a client-only class could never be asked about.
     *
     * <p>Vanilla's furnace geometry, so there is room for a meter, a fuel row, a readout and the player
     * inventory without any of them landing on each other. The readout is a whole font line wide enough
     * for "20,000 / 20,000 FE"; it is declared as a region so the overlap sweep can see it, since text
     * that does not fit its box is exactly how this screen shipped broken.
     */
    public static final ScreenLayout LAYOUT = ScreenLayout.builder(GuiTheme.PANEL_W, GuiTheme.PANEL_H)
        .panel()
        .well("meter", 8, 17, 14, 54)
        .slotRow("fuel", FUEL_SLOTS, 43, 30)
        .region("readout", 43, 56, 120, 9)
        .playerInventory(84)
        .build();

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
        // Assert the DATA count too, not just the container's. This is the guard the Tree Nursery
        // did not have when #369 widened it: its client-side constructor sized its own data with a
        // literal, the number drifted, and the mismatch surfaced as an IndexOutOfBoundsException on
        // the render thread rather than as a message naming both numbers here. Vanilla's furnace
        // menus have always done this, which is why the Cupola and the two furnace subclasses were
        // never exposed to it.
        checkContainerDataCount(data, DATA_SIZE);
        this.container = container;
        this.data = data;

        // One row of fuel, centred. mayPlace defers to the container so the "only fuel" rule lives in
        // exactly one place and a pipe and a player cannot disagree about it.
        LAYOUT.forEachSlot("fuel", (index, x, y) -> this.addSlot(new Slot(container, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return container.canPlaceItem(this.getContainerSlot(), stack);
            }
        }));
        LAYOUT.forEachPlayerSlot((index, x, y) -> this.addSlot(new Slot(inventory, index, x, y)));
        this.addDataSlots(data);
    }

    public int energy() {
        return this.data.get(0);
    }

    public int energyCapacity() {
        return BurnerGeneratorBlockEntity.CAPACITY;
    }

    /** Slot 1 carries a 0/1 flag rather than the burn ticks; see the block entity for why. */
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
