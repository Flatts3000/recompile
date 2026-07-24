package com.flatts.recompile.content.block;

import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Scrap Workstation's shared-storage network (design P2.10). A member block (sorter, workbench,
 * burn barrel) routes items into the connected bins + barrel through here; the routing rules live in
 * one place, {@link #insert}.
 *
 * <p>No BlockEntity holds the membership - it is the core's fixed blueprint, so the members are read
 * live at their known offsets. Everything is gated on the core being FORMED; a member with no formed
 * core routes nothing (its caller falls back to standalone behavior - the tarp drops on the floor).
 */
public final class WorkstationNetwork {

    private WorkstationNetwork() {
    }

    /**
     * The formed Workstation Core this block is a member of, or null. Found by walking the blueprint:
     * for each cell whose component is this block, the core would sit at {@code memberPos - offset};
     * if a formed core is there, that is it.
     */
    @Nullable
    public static BlockPos findCore(Level level, BlockPos memberPos) {
        WorkstationCoreBlock coreBlock = RCBlocks.WORKSTATION_CORE.get();
        Block member = level.getBlockState(memberPos).getBlock();
        for (Multiblock.Cell cell : coreBlock.blueprint().cells()) {
            if (cell.component() == member) {
                BlockPos core = memberPos.subtract(cell.offset());
                BlockState state = level.getBlockState(core);
                if (state.is(coreBlock) && MultiblockCoreBlock.isFormed(state)) {
                    return core;
                }
            }
        }
        return null;
    }

    /** The connected bins, in blueprint order. */
    public static List<ScrapBinBlockEntity> bins(Level level, BlockPos core) {
        List<ScrapBinBlockEntity> bins = new ArrayList<>();
        for (Multiblock.Cell cell : RCBlocks.WORKSTATION_CORE.get().blueprint().cells()) {
            if (cell.component() == RCBlocks.SCRAP_BIN.get()
                    && level.getBlockEntity(cell.at(core)) instanceof ScrapBinBlockEntity bin) {
                bins.add(bin);
            }
        }
        return bins;
    }

    /** The connected Scrap Barrel as a container, or null. */
    @Nullable
    public static Container barrel(Level level, BlockPos core) {
        for (Multiblock.Cell cell : RCBlocks.WORKSTATION_CORE.get().blueprint().cells()) {
            if (cell.component() == RCBlocks.SCRAP_BARREL.get()
                    && level.getBlockEntity(cell.at(core)) instanceof Container container) {
                return container;
            }
        }
        return null;
    }

    /**
     * Route a stack into the connected storage: a bin already bound to its material first, then (only
     * if {@code autoBind}) an empty bin that binds to it, then the barrel. Mutates the stack and
     * returns it - empty if fully stored, otherwise the remainder (all storage full).
     */
    public static ItemStack insert(Level level, BlockPos core, ItemStack stack, boolean autoBind) {
        if (stack.isEmpty()) {
            return stack;
        }
        List<ScrapBinBlockEntity> bins = bins(level, core);
        for (ScrapBinBlockEntity bin : bins) {
            if (bin.boundMaterial() == stack.getItem()) {
                bin.deposit(stack);
                if (stack.isEmpty()) {
                    return stack;
                }
            }
        }
        if (autoBind) {
            for (ScrapBinBlockEntity bin : bins) {
                if (bin.boundMaterial() == null) {
                    bin.deposit(stack);   // depositing into an empty bin binds it
                    if (stack.isEmpty()) {
                        return stack;
                    }
                }
            }
        }
        Container barrel = barrel(level, core);
        if (barrel != null) {
            insertIntoContainer(barrel, stack);
        }
        return stack;
    }

    /**
     * Convenience for a member: find the core and route. Returns the remainder, or the stack unchanged
     * if there is no formed core (the caller then does its standalone thing).
     */
    public static ItemStack insertFromMember(Level level, BlockPos memberPos, ItemStack stack, boolean autoBind) {
        BlockPos core = findCore(level, memberPos);
        return core == null ? stack : insert(level, core, stack, autoBind);
    }

    /** Standard container insertion: merge into matching stacks, then fill empty slots. */
    private static void insertIntoContainer(Container container, ItemStack stack) {
        int size = container.getContainerSize();
        for (int slot = 0; slot < size && !stack.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int cap = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
                int move = Math.min(cap - existing.getCount(), stack.getCount());
                if (move > 0) {
                    existing.grow(move);
                    stack.shrink(move);
                    container.setChanged();
                }
            }
        }
        for (int slot = 0; slot < size && !stack.isEmpty(); slot++) {
            if (container.getItem(slot).isEmpty() && container.canPlaceItem(slot, stack)) {
                int move = Math.min(stack.getMaxStackSize(), stack.getCount());
                container.setItem(slot, stack.split(move));
                container.setChanged();
            }
        }
    }
}
