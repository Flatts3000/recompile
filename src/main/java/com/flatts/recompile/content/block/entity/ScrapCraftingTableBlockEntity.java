package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Holds the Scrap Crafting Table's 3x3 grid so an in-progress pattern survives closing the screen
 * (design P2.10 follow-up - the Tinkers' Crafting Station keeps its grid; vanilla drops it).
 *
 * <p><b>The grid is only ever in one place at a time,</b> which is what keeps it from duplicating: on
 * open the menu <em>moves</em> the stored grid into its transient craft container ({@link #loadInto},
 * which empties the BE); while open the grid lives in the menu; on close the menu writes it back
 * ({@link #saveFrom}). So breaking the table while it is open drops nothing from here (the BE is empty
 * - the open menu drops its own grid), and breaking it while closed drops the stored grid
 * ({@link #preRemoveSideEffects}).
 */
public class ScrapCraftingTableBlockEntity extends BlockEntity {

    private static final int GRID_SIZE = 9;

    private final NonNullList<ItemStack> grid = NonNullList.withSize(GRID_SIZE, ItemStack.EMPTY);

    /**
     * Whether an open menu currently owns the grid. Transient (not saved) - it only guards concurrent
     * openers. The first opener checks out and owns the persistent grid; a second opener while it is
     * checked out gets a plain transient grid that never writes back, so it cannot wipe the owner's.
     */
    private boolean checkedOut;

    public ScrapCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.SCRAP_CRAFTING_TABLE.get(), pos, state);
    }

    /**
     * Claim the grid for one opener. Returns true (and the caller should {@link #loadInto} + persist on
     * close) only if no one else holds it; false means open a non-persisting transient grid instead.
     */
    public boolean tryCheckOut() {
        if (this.checkedOut) {
            return false;
        }
        this.checkedOut = true;
        return true;
    }

    /** Move the stored grid into the menu's craft container, emptying this BE (the grid is now live). */
    public void loadInto(CraftingContainer craftSlots) {
        for (int i = 0; i < GRID_SIZE; i++) {
            craftSlots.setItem(i, this.grid.get(i));
            this.grid.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    /** Write the owner's craft container back into this BE and release the check-out (on close). */
    public void saveFrom(CraftingContainer craftSlots) {
        for (int i = 0; i < GRID_SIZE; i++) {
            this.grid.set(i, craftSlots.getItem(i));
        }
        this.checkedOut = false;
        setChanged();
    }

    /** Drop the stored grid into the world (used by the loot path on break). */
    public void dropContents(Level level) {
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack stack = this.grid.get(i);
            if (!stack.isEmpty()) {
                Block.popResource(level, this.worldPosition, stack);
            }
            this.grid.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    /** Any removal (break, explosion, replace) drops whatever the BE still holds - never lose the grid. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
        super.preRemoveSideEffects(pos, oldState);
        if (this.level != null && !this.level.isClientSide()) {
            dropContents(this.level);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.grid);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.grid.replaceAll(ignored -> ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.grid);
    }
}
