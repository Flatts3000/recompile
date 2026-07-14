package com.flatts.recompile.content.menu;

import com.flatts.recompile.content.block.entity.SortingTarpBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Menu for the Sorting Tarp: one input slot, one screen (modifier) slot, six
 * accumulating output slots, plus the player inventory.
 */
public class SortingTarpMenu extends AbstractContainerMenu {

    private static final int INPUT_X = 26;
    private static final int INPUT_Y = 20;
    private static final int SCREEN_X = 26;
    private static final int SCREEN_Y = 48;
    private static final int OUTPUT_X = 98;
    private static final int OUTPUT_Y = 17;
    private static final int INV_X = 8;
    private static final int INV_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final ContainerLevelAccess access;
    private final ContainerData dataAccess;

    public SortingTarpMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolve(playerInv, buf.readBlockPos()),
            new SimpleContainerData(SortingTarpBlockEntity.DATA_COUNT));
    }

    public SortingTarpMenu(int containerId, Inventory playerInv, @Nullable SortingTarpBlockEntity be, ContainerData data) {
        super(RCMenuTypes.SORTING_TARP.get(), containerId);
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.dataAccess = data;

        ItemStackHandler inv = be != null ? be.getInventory() : new ItemStackHandler(SortingTarpBlockEntity.SLOT_COUNT);

        addSlot(new SlotItemHandler(inv, SortingTarpBlockEntity.INPUT_SLOT, INPUT_X, INPUT_Y));
        addSlot(new SlotItemHandler(inv, SortingTarpBlockEntity.SCREEN_SLOT, SCREEN_X, SCREEN_Y));
        for (int i = 0; i < SortingTarpBlockEntity.OUTPUT_COUNT; i++) {
            int col = i % 3;
            int row = i / 3;
            addSlot(new SlotItemHandler(inv, SortingTarpBlockEntity.OUTPUT_START + i,
                OUTPUT_X + col * 18, OUTPUT_Y + row * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false; // outputs are machine-filled only
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INV_X + col * 18, HOTBAR_Y));
        }

        addDataSlots(dataAccess);
    }

    public int getProgress() {
        return dataAccess.get(SortingTarpBlockEntity.DATA_PROGRESS);
    }

    public int getTotal() {
        int total = dataAccess.get(SortingTarpBlockEntity.DATA_TOTAL);
        return total > 0 ? total : SortingTarpBlockEntity.PROCESS_TICKS;
    }

    @Nullable
    private static SortingTarpBlockEntity resolve(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof SortingTarpBlockEntity tarp) {
            return tarp;
        }
        return null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return copy;
        }
        ItemStack stack = slot.getItem();
        copy = stack.copy();

        int containerEnd = SortingTarpBlockEntity.SLOT_COUNT;
        int playerStart = containerEnd;
        int playerEnd = containerEnd + 36;

        if (slotIndex < containerEnd) {
            // From the machine to the player inventory.
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From the player inventory into the machine's input or screen slot.
            if (!moveItemStackTo(stack, SortingTarpBlockEntity.INPUT_SLOT, SortingTarpBlockEntity.SCREEN_SLOT + 1, false)) {
                // Otherwise cycle within the player inventory.
                int mainEnd = containerEnd + 27;
                if (slotIndex < mainEnd) {
                    if (!moveItemStackTo(stack, mainEnd, playerEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(stack, playerStart, mainEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, RCBlocks.SORTING_TARP.get());
    }
}
