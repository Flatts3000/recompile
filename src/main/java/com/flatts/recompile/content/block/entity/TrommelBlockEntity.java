package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.block.ScrapNetwork;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.TrommelCoreBlock;
import com.flatts.recompile.content.block.TrommelDrumBlock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.event.RCAnalytics;
import com.flatts.recompile.registry.RCBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Trommel's brain: one sortable block per {@value #SORT_TICKS} ticks at {@value #SORT_ENERGY}
 * FE/tick, which is exactly what the Sorting Tarp yields by hand.
 *
 * <p><b>Rate parity is structural, not a promise.</b> Rolls and pull table both come from
 * {@link SortableBlock}, so the machine and the station it automates read the same numbers and cannot
 * drift. The reward for building one is that it runs unattended.
 *
 * <p><b>No inventory, and that is the design.</b> It is not a {@code Container} and exposes no item
 * capability, so nothing can push into it and no pipe can connect. It automates by reaching out
 * instead: it swallows loose items dropped along the drum. A hopper under the chute catches what falls.
 */
public class TrommelBlockEntity extends BlockEntity {

    private static final int BUFFER = 4000;

    /** One block per this many ticks, matching the Separator's old sorting mode exactly. */
    public static final int SORT_TICKS = 40;

    /** FE per tick while turning. */
    public static final int SORT_ENERGY = 16;

    public static final int QUEUE_SLOTS = 9;

    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(BUFFER, BUFFER, BUFFER);

    private final NonNullList<ItemStack> queue = NonNullList.withSize(QUEUE_SLOTS, ItemStack.EMPTY);

    private int progress;

    private int goal;

    /** What the head slot held when this run started, so a swap cannot bank one item's progress. */
    private ItemStack sorting = ItemStack.EMPTY;


    public TrommelBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.TROMMEL.get(), pos, state);
    }

    public SimpleEnergyHandler battery() {
        return battery;
    }

    public int progress() {
        return progress;
    }

    public int goal() {
        return goal;
    }

    public List<ItemStack> queued() {
        return queue;
    }

    public int queuedCount() {
        int total = 0;
        for (ItemStack stack : queue) {
            total += stack.getCount();
        }
        return total;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  TrommelBlockEntity be) {
        if (!MultiblockCoreBlock.isFormed(state)) {
            be.stall(level, pos, state);
            return;
        }

        // Swallow first, then turn, so something dropped in this tick is queued rather than waiting a
        // tick for no reason the player can see.
        be.intake(level, pos);

        int head = be.headSlot(level);
        if (head < 0) {
            be.progress = 0;
            be.goal = 0;
            be.sorting = ItemStack.EMPTY;
            be.stall(level, pos, state);
            return;
        }
        ItemStack stack = be.queue.get(head);
        int rolls = SortableBlock.sortRolls(stack.getItem());
        if (rolls <= 0) {
            be.stall(level, pos, state);
            return;
        }

        // A different item at the head means a different run. Banking one item's progress against
        // another would let a player start one block and finish it with a cheaper one.
        if (!ItemStack.isSameItem(be.sorting, stack)) {
            be.sorting = stack.copy();
            be.progress = 0;
        }
        be.goal = SORT_TICKS;

        try (Transaction tx = Transaction.openRoot()) {
            if (be.battery.extract(SORT_ENERGY, tx) < SORT_ENERGY) {
                // Underpowered: hold progress and go dark. The queue waits; nothing is lost.
                be.stall(level, pos, state);
                return;
            }
            tx.commit();
        }

        be.setActive(level, pos, state, true);
        be.progress++;
        if (be.progress >= SORT_TICKS) {
            be.progress = 0;
            be.sort(level, pos, head, rolls);
        }
        be.setChanged();
    }

    /**
     * The first slot holding something this machine can sort, or -1.
     *
     * <p>Also the self-heal: anything queued that is no longer sortable goes out of the chute rather
     * than blocking the head forever. That can only happen when a datapack changes under a saved world,
     * and a machine bricked by a pack update is a far worse outcome than an item on the floor.
     */
    private int headSlot(ServerLevel level) {
        for (int slot = 0; slot < queue.size(); slot++) {
            ItemStack stack = queue.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (SortableBlock.sortRolls(stack.getItem()) > 0) {
                return slot;
            }
            deliverToChute(level, TrommelCoreBlock.outlet(level, worldPosition),
                TrommelCoreBlock.dischargeFacing(level, worldPosition).getOpposite(), stack.copy());
            queue.set(slot, ItemStack.EMPTY);
            setChanged();
        }
        return -1;
    }

    private boolean accepts(ItemStack stack) {
        return SortableBlock.sortRolls(stack.getItem()) > 0;
    }

    /** Swallow loose items lying anywhere along the drum. */
    private void intake(ServerLevel level, BlockPos pos) {
        AABB mouth = TrommelCoreBlock.mouth(level, pos);
        if (mouth == null) {
            return;
        }
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, mouth)) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || !accepts(stack)) {
                continue;   // nothing to do with it: leave it lying there rather than swallow it
            }
            ItemStack left = insert(stack);
            if (left.isEmpty()) {
                entity.discard();
            } else if (left.getCount() != stack.getCount()) {
                // Only when something actually moved. A full queue with an item sitting in the mouth
                // would otherwise re-set the same stack every tick, and setItem syncs the entity to
                // every client watching it.
                entity.setItem(left);
            }
        }

        // A CONTAINER PARKED ON THE DRUM IS DRAINED, exactly as the Separator drains one parked on its
        // chamber. This is the route a player will actually try, and until it existed the machine's
        // only way in was throwing loose items at it - which nothing announces, so the honest reading
        // of a Trommel you had just built was that it did not work.
        //
        // It is also what makes the machine automatable without opening a door in it: a hopper fills
        // the chest, the machine sips from the chest. Nothing can push INTO the Trommel, which is the
        // property the closed-door test defends; reaching out and being reached into stay different
        // doors, and only the second is shut.
        for (BlockPos cell : TrommelCoreBlock.drumCells(level, pos)) {
            Container container = HopperBlockEntity.getContainerAt(level, cell.above());
            if (container == null) {
                continue;
            }
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !accepts(stack)) {
                    continue;
                }
                // One slot's worth per tick, so the machine sips from a chest rather than vacuuming it
                // in a single frame.
                ItemStack left = insert(stack);
                if (left.getCount() != stack.getCount()) {
                    container.setItem(slot, left.isEmpty() ? ItemStack.EMPTY : left);
                    container.setChanged();
                }
                // Whether or not anything moved: a full queue must not spin through the rest of the
                // container's slots, and setChanged on a chest every tick is chunk-save churn.
                break;
            }
        }
    }

    private ItemStack insert(ItemStack incoming) {
        ItemStack stack = incoming.copy();
        for (int slot = 0; slot < queue.size() && !stack.isEmpty(); slot++) {
            ItemStack held = queue.get(slot);
            if (held.isEmpty() || !ItemStack.isSameItemSameComponents(held, stack)) {
                continue;
            }
            int room = Math.min(held.getMaxStackSize(), 64) - held.getCount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, stack.getCount());
            held.grow(moved);
            stack.shrink(moved);
            setChanged();
        }
        for (int slot = 0; slot < queue.size() && !stack.isEmpty(); slot++) {
            if (!queue.get(slot).isEmpty()) {
                continue;
            }
            int moved = Math.min(Math.min(stack.getMaxStackSize(), 64), stack.getCount());
            queue.set(slot, stack.split(moved));
            setChanged();
        }
        return stack;
    }

    /**
     * Consume one sortable block and roll its pull stream, exactly as the Sorting Tarp does.
     *
     * <p>Rolls and table both come from {@link SortableBlock}, which is what makes "the Trommel yields
     * what the tarp yields" a fact about the code rather than a claim in a document.
     */
    private void sort(ServerLevel level, BlockPos pos, int slot, int rolls) {
        ItemStack stack = queue.get(slot);
        var key = SortableBlock.pullTableFor(stack.getItem());
        if (key == null) {
            return;
        }
        // CAPTURED BEFORE THE SHRINK. Taking it after means the last item in a slot has already
        // become an empty stack, whose getItem() is AIR - so every roll for that block was recorded
        // as sifted "from minecraft:air". A slot usually drains to one before emptying, so this
        // corrupted a real fraction of the rows the instrumentation test exists to require.
        Item sifted = stack.getItem();
        stack.shrink(1);
        if (stack.isEmpty()) {
            queue.set(slot, ItemStack.EMPTY);
            sorting = ItemStack.EMPTY;
        }

        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .create(LootContextParamSets.CHEST);
        BlockPos outlet = TrommelCoreBlock.outlet(level, pos);
        // ALONG THE RUN, not along FACING. The two are ninety degrees apart: outlet() is
        // core + rotate((LENGTH,1,0)), so the drum runs EAST for a NORTH-facing machine, while a
        // FACING-derived direction threw every item sideways off the machine - past the container the
        // player parked at the end, and telling a WorldlyContainer it was entered through the wrong
        // face. dischargeFacing exists for exactly this and nothing was calling it.
        //
        // `entry` is the face material ENTERS a container through, so it is the run reversed.
        Direction entry = TrommelCoreBlock.dischargeFacing(level, pos).getOpposite();
        for (int i = 0; i < rolls; i++) {
            List<ItemStack> pulled = table.getRandomItems(params);
            RCAnalytics.sifted("TROMMEL", sifted, pulled);
            for (ItemStack drop : pulled) {
                if (!drop.isEmpty()) {
                    deliver(level, pos, outlet, entry, drop);
                }
            }
        }
        level.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 0.5F, 0.9F);
    }

    private static void deliver(ServerLevel level, BlockPos member, BlockPos outlet, Direction entry,
                                ItemStack stack) {
        // THE SCRAP NETWORK FIRST, the way the Sorting Tarp already routes what it sifts: a machine
        // that made you empty its chute by hand would be a worse tarp. autoBind is false, because the
        // file-all is the only caller allowed to type an empty bin - a sort must never surprise-bind
        // one and quietly claim a wall slot for whatever happened to come out first.
        ItemStack remainder = ScrapNetwork.insertFromMember(level, member, stack, false);
        if (remainder.isEmpty()) {
            return;
        }
        deliverToChute(level, outlet, entry, remainder);
    }

    /**
     * Discharge off the end of the drum: into a container if one is parked there, otherwise thrown
     * clear like a dispenser.
     *
     * <p>Container first is the whole point - a machine that spits items onto the floor while a chest
     * sits in the discharge is not automatable, and picking them up by hand is worse than not having
     * built it. Throwing is the fallback, not the design.
     */
    private static void deliverToChute(ServerLevel level, BlockPos outlet, Direction entry,
                                       ItemStack stack) {
        var handler = level.getCapability(Capabilities.Item.BLOCK, outlet, null);
        if (handler != null && !stack.isEmpty()) {
            try (Transaction tx = Transaction.openRoot()) {
                int accepted = handler.insert(ItemResource.of(stack), stack.getCount(), tx);
                if (accepted > 0) {
                    tx.commit();
                    stack.shrink(accepted);
                }
            }
        }
        if (!stack.isEmpty()) {
            Container container = HopperBlockEntity.getContainerAt(level, outlet);
            if (container != null) {
                // Vanilla's own helper, NOT a hand-rolled slot loop: it honours canPlaceItem and, for a
                // WorldlyContainer, getSlotsForFace. That matters more than it looks - the Burn Barrel
                // deliberately returns no slots on any face to keep automation out of its smelt slots,
                // and a raw setItem loop would walk straight past that.
                stack = HopperBlockEntity.addItem(null, container, stack, entry);
            }
        }
        if (!stack.isEmpty()) {
            // THROWN, not dropped. popResource scatters with random velocity, which from a block at
            // drum height rains material down the outside of the machine. A trommel delivers along its
            // own axis, so this leaves the same way: out the open end, with the run's direction.
            Direction out = entry.getOpposite();
            ItemEntity thrown = new ItemEntity(level,
                outlet.getX() + 0.5 + out.getStepX() * 0.3,
                outlet.getY() + 0.3,
                outlet.getZ() + 0.5 + out.getStepZ() * 0.3,
                stack);
            thrown.setDeltaMovement(out.getStepX() * 0.16, 0.02, out.getStepZ() * 0.16);
            thrown.setDefaultPickUpDelay();
            level.addFreshEntity(thrown);
        }
    }

    /**
     * Any removal - break, explosion, replace - hands the queue back. A machine that eats what it was
     * holding is worse than one that never accepted it.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
        if (level != null && !level.isClientSide()) {
            for (int slot = 0; slot < queue.size(); slot++) {
                ItemStack stack = queue.get(slot);
                if (!stack.isEmpty()) {
                    Block.popResource(level, pos, stack);
                    queue.set(slot, ItemStack.EMPTY);
                }
            }
        }
        super.preRemoveSideEffects(pos, oldState);
    }

    private void stall(Level level, BlockPos pos, BlockState state) {
        setActive(level, pos, state, false);
    }

    /** Mirror ACTIVE onto every drum cell, which is what makes the animated texture reachable. */
    private void setActive(Level level, BlockPos pos, BlockState state, boolean active) {
        if (!state.hasProperty(TrommelCoreBlock.ACTIVE)
                || state.getValue(TrommelCoreBlock.ACTIVE) == active) {
            return;
        }
        level.setBlock(pos, state.setValue(TrommelCoreBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        for (BlockPos cell : TrommelCoreBlock.drumCells(level, pos)) {
            BlockState cellState = level.getBlockState(cell);
            if (cellState.hasProperty(TrommelDrumBlock.ACTIVE)
                    && cellState.getValue(TrommelDrumBlock.ACTIVE) != active) {
                level.setBlock(cell, cellState.setValue(TrommelDrumBlock.ACTIVE, active),
                    Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        battery.serialize(output.child("energy"));
        output.putInt("progress", progress);
        output.putInt("goal", goal);
        ContainerHelper.saveAllItems(output, queue);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("energy").ifPresent(battery::deserialize);
        progress = input.getIntOr("progress", 0);
        goal = input.getIntOr("goal", 0);
        // Reset IN PLACE. NonNullList.withSize is fixed-size, so clear() and add() both throw, and the
        // throw aborts the whole of loadAdditional - which on the Separator took the stored energy down
        // with the queue, so the machine came back from a reload empty and cold with one log line to
        // show for it.
        for (int slot = 0; slot < queue.size(); slot++) {
            queue.set(slot, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input, queue);
        // RESTORE `sorting` FROM THE QUEUE. It is not serialized, so after a reload it was empty and
        // serverTick's "different item at the head means a different run" check fired immediately and
        // reset progress to 0 - which made the progress and goal written just above dead weight, and
        // silently restarted a part-sorted block on every server restart.
        sorting = ItemStack.EMPTY;
        for (ItemStack stack : queue) {
            if (!stack.isEmpty() && SortableBlock.sortRolls(stack.getItem()) > 0) {
                sorting = stack.copy();
                break;
            }
        }
    }
}
