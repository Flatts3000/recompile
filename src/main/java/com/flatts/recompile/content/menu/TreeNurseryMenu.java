package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.entity.TreeNurseryBlockEntity;
import com.flatts.recompile.registry.RCItems;
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
 * The Tree Nursery's menu (spec {@code docs/tree_nursery_spec.md}): the mod's <b>second</b> bespoke
 * screen (a recorded reversal), earned because species selection has no vanilla-screen analog and
 * cannot be an inserted-item template (saplings cannot be held as an input - that is the whole loot
 * strip). Three slots - Fertilizer in, Unknown Seedling in, sapling out - plus a species picker driven
 * through {@link #clickMenuButton} (the vanilla Stonecutter/Loom no-custom-packet path) and a
 * {@link ContainerData} feeding the water gauge and progress arrow.
 */
public class TreeNurseryMenu extends AbstractContainerMenu {

    public static final int SLOT_FERTILIZER = 0;
    public static final int SLOT_SEEDLING = 1;
    public static final int SLOT_OUTPUT = 2;
    private static final int MACHINE_SLOTS = 3;
    private static final int INV_START = MACHINE_SLOTS;
    private static final int INV_MAIN_END = INV_START + 27;   // 3 rows of the backpack
    private static final int INV_END = INV_START + 36;        // + the hotbar

    private final Container container;
    private final ContainerData data;

    /** Client factory: a dummy container + data, filled by the sync (see {@link RCMenus}). */
    public TreeNurseryMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(MACHINE_SLOTS), new SimpleContainerData(5));
    }

    public TreeNurseryMenu(int containerId, Inventory inventory, Container container, ContainerData data) {
        super(RCMenus.TREE_NURSERY.get(), containerId);
        checkContainerSize(container, MACHINE_SLOTS);
        this.container = container;
        this.data = data;

        // Fertilizer + Unknown Seedling inputs, and the take-only sapling output.
        this.addSlot(new Slot(container, SLOT_FERTILIZER, 44, 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(RCItems.FERTILIZER.get());
            }
        });
        this.addSlot(new Slot(container, SLOT_SEEDLING, 62, 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(RCItems.UNKNOWN_SEEDLING.get());
            }
        });
        this.addSlot(new Slot(container, SLOT_OUTPUT, 116, 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Player inventory - below the two-row species picker (see TreeNurseryScreen, H = 184).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 102 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 160));
        }

        this.addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    /**
     * The species picker: a button id in {@code [0, SPECIES.length)} selects that sapling. Server-side
     * the container IS the nursery BE, so set it there; the id travels as a VAR_INT, no custom packet.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < TreeNurseryBlockEntity.SPECIES.length
                && this.container instanceof TreeNurseryBlockEntity nursery) {
            nursery.setSelectedSpecies(id);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return moved;
        }
        ItemStack inSlot = slot.getItem();
        moved = inSlot.copy();
        if (index == SLOT_OUTPUT) {
            // Sapling out to the inventory; onQuickCraft so a full run empties in one shift-click.
            if (!this.moveItemStackTo(inSlot, INV_START, INV_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(inSlot, moved);
        } else if (index == SLOT_FERTILIZER || index == SLOT_SEEDLING) {
            if (!this.moveItemStackTo(inSlot, INV_START, INV_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From the player inventory: route to the matching input, else shuffle backpack <-> hotbar.
            if (inSlot.is(RCItems.FERTILIZER.get())) {
                if (!this.moveItemStackTo(inSlot, SLOT_FERTILIZER, SLOT_FERTILIZER + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (inSlot.is(RCItems.UNKNOWN_SEEDLING.get())) {
                if (!this.moveItemStackTo(inSlot, SLOT_SEEDLING, SLOT_SEEDLING + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < INV_MAIN_END) {
                if (!this.moveItemStackTo(inSlot, INV_MAIN_END, INV_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(inSlot, INV_START, INV_MAIN_END, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (inSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (inSlot.getCount() == moved.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, inSlot);
        return moved;
    }

    // ---------------- screen readouts ----------------

    public int cookProgress() {
        return this.data.get(TreeNurseryBlockEntity.DATA_COOK);
    }

    public int cookTotal() {
        return this.data.get(TreeNurseryBlockEntity.DATA_COOK_TOTAL);
    }

    public int water() {
        return this.data.get(TreeNurseryBlockEntity.DATA_WATER);
    }

    public int waterCapacity() {
        return this.data.get(TreeNurseryBlockEntity.DATA_WATER_CAP);
    }

    public int selectedSpecies() {
        return this.data.get(TreeNurseryBlockEntity.DATA_SPECIES);
    }
}
