package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.block.GarbageBlock;
import com.flatts.recompile.content.block.TrashBagBlock;
import com.flatts.recompile.content.menu.SortingTarpMenu;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The Sorting Tarp (design P1.3): the batch tier of the sort verb. Drop garbage
 * blocks, bags, or bales in the input slot and it auto-sorts them into the base
 * materials over time (no click-spam), accumulating outputs. A screen in the
 * modifier slot skews <i>what</i> comes out, not how much. Hopper compat is off at
 * this tier (no capability provider) - manual by identity.
 */
public class SortingTarpBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUT_SLOT = 0;
    public static final int SCREEN_SLOT = 1;
    public static final int OUTPUT_START = 2;
    public static final int OUTPUT_COUNT = 6;
    public static final int SLOT_COUNT = OUTPUT_START + OUTPUT_COUNT;

    public static final int PROCESS_TICKS = 40;
    private static final float SCREEN_SKEW_CHANCE = 0.45F;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_COUNT = 2;

    private int progress = 0;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == INPUT_SLOT) {
                return outputRolls(stack.getItem()) > 0;
            }
            if (slot == SCREEN_SLOT) {
                return stack.is(RCItems.METAL_SCREEN.get()) || stack.is(RCItems.ORGANICS_SCREEN.get());
            }
            // Output slots accept anything: the machine fills them via insertItem
            // (which honors isItemValid), and the menu blocks manual player placement.
            return true;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> PROCESS_TICKS;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_PROGRESS) {
                progress = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public SortingTarpBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.SORTING_TARP.get(), pos, state);
    }

    /** How many material rolls one of this input yields (0 = not a valid input). */
    private static int outputRolls(Item item) {
        if (item == RCItems.GARBAGE_BLOCK.get().asItem()) {
            return 5;
        }
        if (item == RCItems.TRASH_BAG.get().asItem()) {
            return 2;
        }
        if (item == RCItems.COMPACTED_BALE.get().asItem()) {
            return 12;
        }
        return 0;
    }

    private static ResourceKey<LootTable> pullTableFor(Item item) {
        if (item == RCItems.TRASH_BAG.get().asItem()) {
            return TrashBagBlock.BAG_PULLS;
        }
        return GarbageBlock.HOUSEHOLD_PULLS; // garbage block + dense bale
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SortingTarpBlockEntity be) {
        ItemStack input = be.inventory.getStackInSlot(INPUT_SLOT);
        if (outputRolls(input.getItem()) <= 0) {
            be.resetProgress();
            return;
        }
        if (be.outputsFull()) {
            return; // nowhere to put results - pause, keep progress (furnace behaviour)
        }
        be.progress++;
        be.setChanged();
        if (be.progress >= PROCESS_TICKS) {
            be.progress = 0;
            be.sortOne((ServerLevel) level, pos, input);
        }
    }

    private void sortOne(ServerLevel level, BlockPos pos, ItemStack input) {
        int rolls = outputRolls(input.getItem());
        LootTable table = level.getServer().reloadableRegistries().getLootTable(pullTableFor(input.getItem()));
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .create(LootContextParamSets.CHEST);

        Item screenTarget = screenTarget();
        input.shrink(1);
        for (int i = 0; i < rolls; i++) {
            List<ItemStack> pulled = table.getRandomItems(params);
            for (ItemStack drop : pulled) {
                if (drop.isEmpty()) {
                    continue;
                }
                deposit(level, pos, applyScreen(drop, screenTarget, level));
            }
        }
    }

    /** Screens change what, not how much: swap some junk for the screen's target material. */
    private ItemStack applyScreen(ItemStack drop, Item screenTarget, ServerLevel level) {
        if (screenTarget != null
            && drop.is(RCItems.JUNK.get())
            && level.getRandom().nextFloat() < SCREEN_SKEW_CHANCE) {
            return new ItemStack(screenTarget, drop.getCount());
        }
        return drop;
    }

    private Item screenTarget() {
        ItemStack screen = inventory.getStackInSlot(SCREEN_SLOT);
        if (screen.is(RCItems.METAL_SCREEN.get())) {
            return RCItems.SCRAP_METAL.get();
        }
        if (screen.is(RCItems.ORGANICS_SCREEN.get())) {
            return RCItems.ORGANIC_MUCK.get();
        }
        return null;
    }

    /** Insert into output slots; overflow pops above the tarp so nothing is lost. */
    private void deposit(ServerLevel level, BlockPos pos, ItemStack stack) {
        ItemStack remaining = stack;
        for (int slot = OUTPUT_START; slot < SLOT_COUNT && !remaining.isEmpty(); slot++) {
            remaining = inventory.insertItem(slot, remaining, false);
        }
        if (!remaining.isEmpty()) {
            Block.popResource(level, pos.above(), remaining);
        }
    }

    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            setChanged();
        }
    }

    /** True when no output slot can accept anything more - the machine must pause. */
    private boolean outputsFull() {
        for (int slot = OUTPUT_START; slot < SLOT_COUNT; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    /** Drop the whole inventory (called by the block on break). */
    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", progress);
        inventory.serialize(output.child("Inventory"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = Math.max(0, input.getIntOr("Progress", 0));
        input.child("Inventory").ifPresent(inventory::deserialize);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.recompile.sorting_tarp");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new SortingTarpMenu(containerId, playerInv, this, dataAccess);
    }
}
