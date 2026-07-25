package com.flatts.recompile.content.block;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.ScrapBinBlockEntity;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCTags;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The scrap network: scrap blocks placed touching each other form one connected cluster, and junk
 * routes between them (design P2.10). Adjacency, not a blueprint - place the blocks however you like
 * and they wire up; move one away and it drops off. There is no core, no controller, and
 * <b>no saved state</b>: each interaction floods outward from the acting block over
 * {@link RCTags#SCRAP_CONNECTABLE} and reads the members live. Clusters are small and interactions are
 * user-paced, so a fresh flood per call is cheap.
 *
 * <p><b>Only two of the six member types are routing sinks.</b> A {@link ScrapBinBlockEntity} (bind /
 * deposit) and the Scrap Barrel (its {@link Container}, matched by block id). The Burn Barrel is in
 * the tag so a smelter wired into the cluster still conducts, but it is a furnace
 * {@link net.minecraft.world.WorldlyContainer} - routing must never land in its smelt slots, so it is
 * matched by neither branch. The sorter, workbench and crafting table are conductors too, never sinks.
 */
public final class ScrapNetwork {

    /** Flood-fill ceiling: a runaway-cluster backstop, far above any sane bench. */
    private static final int MAX_MEMBERS = 256;

    private ScrapNetwork() {
    }

    /**
     * Every scrap block reachable from {@code start} through face-adjacent members (including
     * {@code start} itself if it is one). A bounded breadth-first flood over
     * {@link RCTags#SCRAP_CONNECTABLE}; if a cluster somehow exceeds {@link #MAX_MEMBERS} it stops and
     * logs, routing into the portion already found rather than hanging.
     */
    public static List<BlockPos> collect(Level level, BlockPos start) {
        List<BlockPos> members = new ArrayList<>();
        if (!level.getBlockState(start).is(RCTags.SCRAP_CONNECTABLE)) {
            return members;
        }
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty() && members.size() < MAX_MEMBERS) {
            BlockPos pos = queue.poll();
            members.add(pos);
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (seen.contains(next)) {
                    continue;
                }
                if (level.getBlockState(next).is(RCTags.SCRAP_CONNECTABLE)) {
                    seen.add(next.immutable());
                    queue.add(next.immutable());
                }
            }
        }
        if (members.size() >= MAX_MEMBERS && !queue.isEmpty()) {
            Recompile.LOGGER.warn("Scrap network at {} hit the {}-block cap; routing into the found portion",
                start, MAX_MEMBERS);
        }
        return members;
    }

    /** The bins among the given members, in flood order. */
    public static List<ScrapBinBlockEntity> bins(Level level, List<BlockPos> members) {
        List<ScrapBinBlockEntity> bins = new ArrayList<>();
        for (BlockPos pos : members) {
            if (level.getBlockEntity(pos) instanceof ScrapBinBlockEntity bin) {
                bins.add(bin);
            }
        }
        return bins;
    }

    /** The barrels among the given members - only the Scrap Barrel, never any other container. */
    public static List<Container> barrels(Level level, List<BlockPos> members) {
        List<Container> barrels = new ArrayList<>();
        for (BlockPos pos : members) {
            BlockState state = level.getBlockState(pos);
            if (state.is(RCBlocks.SCRAP_BARREL.get())
                    && level.getBlockEntity(pos) instanceof Container container) {
                barrels.add(container);
            }
        }
        return barrels;
    }

    /** True when the cluster reached from {@code member} contains any storage sink (bin or barrel). */
    public static boolean reachesStorage(Level level, BlockPos member) {
        List<BlockPos> members = collect(level, member);
        return !bins(level, members).isEmpty() || !barrels(level, members).isEmpty();
    }

    /**
     * Route a stack into the connected storage from a member block: a bin already bound to the item
     * first, then (only if {@code autoBind}) an empty bin that binds to it, then a barrel. Mutates the
     * stack and returns it - empty if fully stored, otherwise the remainder (all storage full, or no
     * network / no storage, in which case the stack is unchanged and the caller does its standalone
     * thing).
     */
    public static ItemStack insertFromMember(Level level, BlockPos member, ItemStack stack, boolean autoBind) {
        if (stack.isEmpty()) {
            return stack;
        }
        List<BlockPos> members = collect(level, member);
        if (members.isEmpty()) {
            return stack;
        }
        List<ScrapBinBlockEntity> bins = bins(level, members);
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
        for (Container barrel : barrels(level, members)) {
            insertIntoContainer(barrel, stack);
            if (stack.isEmpty()) {
                return stack;
            }
        }
        return stack;
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
