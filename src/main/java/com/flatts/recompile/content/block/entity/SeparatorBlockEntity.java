package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.block.SeparatorChamberBlock;
import com.flatts.recompile.content.block.SeparatorCoreBlock;
import com.flatts.recompile.content.recipe.SeparatingRecipe;
import com.flatts.recompile.content.recipe.TeardownRecipe;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Separator's brain ({@code docs/gem_tier_spec.md} Phase 2).
 *
 * <p><b>It holds a bounded queue, and nothing can reach it.</b> The machine swallows what lands in its
 * bay into {@value #QUEUE_SLOTS} internal slots and works through them in order, so several kinds of
 * scrap can go in at once and come back out as their raw materials without the player standing over it.
 *
 * <p>Internal is the load-bearing word. The queue is <b>not</b> a {@code Container} and the machine
 * exposes no item handler on any side, so no hopper can insert into it and no pipe can connect - the
 * property {@code docs/automation_policy_spec.md} calls the closed door. Automation is still perfectly
 * possible; it just goes through the world, with a dropper over the bay and a hopper under the chute.
 *
 * <p><b>Only what the machine can actually grind is let in.</b> An item with no
 * {@code recompile:separating} recipe is never swallowed, so the queue cannot jam on junk and a player
 * cannot lose something by dropping it in the wrong place - it simply lies in the bay.
 *
 * <p><b>Progress is banked against the head of the queue.</b> Change what is at the head and the run
 * resets; if the power runs out the queue simply waits, the way a furnace with no fuel sits full and
 * cold. Everything queued drops on break, so the machine is never an item sink.
 */
public class SeparatorBlockEntity extends BlockEntity {

    /**
     * Several operations' worth, so a solar gap does not stutter the machine. Not derived from one
     * operation any more: at 1-in-1-out an operation is two seconds, and a buffer that small would
     * empty between panel ticks.
     */
    private static final int BUFFER = 4000;

    /**
     * Insert and extract are both open <b>on the handler</b>; it is the capability wrapper in
     * {@code RCBlockEntities} that makes the machine insert-only to the outside world. Building the
     * handler extract-disabled instead looks like the same thing and is not: the machine could no
     * longer draw its own power, so it sat fully charged and never ran.
     */
    private final SimpleEnergyHandler battery =
        new SimpleEnergyHandler(BUFFER, Integer.MAX_VALUE, Integer.MAX_VALUE);

    /**
     * How many kinds of scrap the machine can hold at once. Bounded on purpose: a machine that swallows
     * an unbounded amount is a storage block, and this one is a grinder with a hopper on it.
     */
    public static final int QUEUE_SLOTS = 9;

    /**
     * The queue. Insertion-ordered and processed from the front, so what went in first comes out first
     * and a player watching the chute sees the order they fed it.
     *
     * <p>A {@code NonNullList} for the serialization alone - {@code ContainerHelper} reads and writes
     * one directly. This block is emphatically <b>not</b> a {@code Container}; see the class note.
     */
    private final NonNullList<ItemStack> queue = NonNullList.withSize(QUEUE_SLOTS, ItemStack.EMPTY);

    private int progress;
    /** The matched recipe's tick target, so Jade can show a percentage rather than a raw count. */
    private int goal;
    /**
     * What the head of the queue was last tick, so a swap resets the run rather than crediting the
     * new item with the old one's progress.
     */
    private ItemStack grinding = ItemStack.EMPTY;
    /**
     * The head slot's count against what the recipe wants.
     *
     * <p>Ships equal at count 1, and exists for the packs that set it higher: a machine that will not
     * say which number it is waiting for has hidden the only thing the player has to act on.
     */
    private int feedHave;
    private int feedNeed;

    public SeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.SEPARATOR.get(), pos, state);
    }

    public SimpleEnergyHandler battery() {
        return battery;
    }

    public int progress() {
        return progress;
    }

    /** Ticks the current run needs, or 0 when nothing is being ground. */
    public int goal() {
        return goal;
    }

    /** How much of the head slot's input is queued. 0 when the queue is empty. */
    public int feedHave() {
        return feedHave;
    }

    /** How much the head slot's recipe wants. 0 when the queue is empty. */
    public int feedNeed() {
        return feedNeed;
    }

    /** The queue, for Jade and the drop-on-break path. Read-only to callers by convention. */
    public List<ItemStack> queued() {
        return queue;
    }

    /** How many items are waiting, across every slot. */
    public int queuedCount() {
        int total = 0;
        for (ItemStack stack : queue) {
            total += stack.getCount();
        }
        return total;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SeparatorBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock.isFormed(state)) {
            be.stall(level, pos, state);
            return;
        }

        // Swallow first, then grind, so something dropped in this tick is queued rather than waiting a
        // tick for no reason the player can see.
        be.intake(server, pos);

        int head = be.headSlot(server);
        if (head < 0) {
            be.progress = 0;
            be.goal = 0;
            be.grinding = ItemStack.EMPTY;
            be.feedHave = 0;
            be.feedNeed = 0;
            be.stall(level, pos, state);
            return;
        }
        ItemStack stack = be.queue.get(head);
        RecipeHolder<SeparatingRecipe> match = be.recipeFor(server, stack);
        if (match == null) {
            be.stall(level, pos, state);
            return;
        }
        SeparatingRecipe recipe = match.value();

        // A different item at the head means a different run. Banking one item's progress against
        // another would let a player start a slow grind and finish it with something cheap.
        if (!ItemStack.isSameItem(be.grinding, stack)) {
            be.grinding = stack.copy();
            be.progress = 0;
        }
        be.goal = recipe.ticks();
        be.feedHave = stack.getCount();
        be.feedNeed = recipe.count();
        if (stack.getCount() < recipe.count()) {
            // Queued, but not enough of it yet for a recipe a pack has set above 1. Wait, do not stall
            // the whole machine - a later slot may well be ready.
            be.stall(level, pos, state);
            return;
        }

        int fe = recipe.energy();
        if (fe > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                if (be.battery.extract(fe, tx) < fe) {
                    // Underpowered: hold progress and go dark. The queue waits; nothing is lost.
                    be.stall(level, pos, state);
                    return;
                }
                tx.commit();
            }
        }

        be.setActive(level, pos, state, true);
        be.progress++;
        if (be.progress >= recipe.ticks()) {
            be.progress = 0;
            be.grind(server, pos, recipe, head);
        }
        be.setChanged();
    }

    /**
     * The first slot holding something this machine can grind, or -1.
     *
     * <p>Also the self-heal: anything queued that no longer has a recipe is thrown out of the chute
     * rather than left to block the head forever. That can only happen when a datapack changes under a
     * saved world, and a machine bricked by a pack update is a far worse outcome than an item on the
     * floor.
     */
    private int headSlot(ServerLevel level) {
        for (int slot = 0; slot < queue.size(); slot++) {
            ItemStack stack = queue.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (recipeFor(level, stack) != null) {
                return slot;
            }
            Block.popResource(level, SeparatorCoreBlock.outlet(level, worldPosition), stack.copy());
            queue.set(slot, ItemStack.EMPTY);
            setChanged();
        }
        return -1;
    }

    private @Nullable RecipeHolder<SeparatingRecipe> recipeFor(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        SingleRecipeInput input = new SingleRecipeInput(stack);
        for (RecipeHolder<SeparatingRecipe> holder
                : level.recipeAccess().recipeMap().byType(RCRecipeTypes.SEPARATING.get())) {
            if (holder.value().matches(input, level)) {
                return holder;
            }
        }
        return null;
    }

    /**
     * Pull what the machine can grind into the queue: loose items lying in the bay first, then a
     * container standing on it.
     *
     * <p><b>The machine reaches out; nothing reaches in.</b> A hopper pointed down at the bay is the
     * first thing anyone tries and can never work, because there is no {@code Container} there to insert
     * into. Draining it instead costs none of the closed-door properties and removes the dead end.
     */
    private void intake(ServerLevel level, BlockPos pos) {
        AABB mouth = SeparatorCoreBlock.mouth(level, pos);
        if (mouth == null) {
            return;
        }
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, mouth)) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || recipeFor(level, stack) == null) {
                continue;   // no recipe: leave it lying there rather than swallow what cannot be ground
            }
            ItemStack left = insert(stack);
            if (left.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(left);
            }
        }

        for (BlockPos cell : SeparatorCoreBlock.chamberCells(level, pos)) {
            Container container = HopperBlockEntity.getContainerAt(level, cell.above());
            if (container == null) {
                continue;
            }
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || recipeFor(level, stack) == null) {
                    continue;
                }
                // One slot's worth per tick, so the machine sips from a chest instead of vacuuming it
                // in a single frame.
                ItemStack left = insert(stack);
                container.setItem(slot, left.isEmpty() ? ItemStack.EMPTY : left);
                container.setChanged();
                break;
            }
        }
    }

    /**
     * Put what fits into the queue and hand back the remainder.
     *
     * <p>Merges into a slot already holding that item before taking an empty one, so nine kinds fit in
     * nine slots rather than one kind spreading across all of them.
     */
    private ItemStack insert(ItemStack incoming) {
        ItemStack stack = incoming;
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
            stack = stack.copyWithCount(stack.getCount() - moved);
            setChanged();
        }
        for (int slot = 0; slot < queue.size() && !stack.isEmpty(); slot++) {
            if (!queue.get(slot).isEmpty()) {
                continue;
            }
            int moved = Math.min(Math.min(stack.getMaxStackSize(), 64), stack.getCount());
            queue.set(slot, stack.copyWithCount(moved));
            stack = stack.copyWithCount(stack.getCount() - moved);
            setChanged();
        }
        return stack;
    }

    /**
     * Consume one operation's worth from a queue slot and throw the results out of the chute.
     *
     * <p>Everything leaves through the single outlet - result and byproducts alike - so one hopper
     * under the chute catches a machine's whole output no matter what a pack writes into the recipe.
     */
    private void grind(ServerLevel level, BlockPos pos, SeparatingRecipe recipe, int slot) {
        ItemStack stack = queue.get(slot);
        if (stack.getCount() < recipe.count()) {
            return;   // drained from under us; nothing was banked, so nothing is lost
        }
        stack.shrink(recipe.count());
        if (stack.isEmpty()) {
            queue.set(slot, ItemStack.EMPTY);
            grinding = ItemStack.EMPTY;
        }

        BlockPos outlet = SeparatorCoreBlock.outlet(level, pos);
        for (TeardownRecipe.ItemResult result : recipe.results()) {
            deliver(level, outlet, result.toStack());
        }
        for (TeardownRecipe.ItemResult byproduct : recipe.byproducts()) {
            deliver(level, outlet, byproduct.toStack());
        }
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 0.6F);
    }

    /**
     * Put one output through the chute: into whatever is standing in front of it, or on the floor.
     *
     * <p><b>The machine pushes; nothing pulls.</b> This is the same reach-out the intake already does
     * at the other end, and it costs none of the closed-door properties - the Separator still exposes
     * no item handler of its own, so no pipe can connect to it and nothing can extract from it. What
     * changes is only that a barrel parked at the chute stops being decoration: the machine was piling
     * its output on the lid of an obviously-correct container, which reads as broken rather than as
     * deliberate.
     *
     * <p>Capability first, then {@code Container}. The capability covers modded storage and vanilla's
     * own through its wrappers; {@code getContainerAt} is the fallback that also handles the things
     * only the hopper path knows about, like a double chest resolving to one inventory. Whatever will
     * not fit falls on the floor, so the machine never destroys what it made.
     */
    private static void deliver(ServerLevel level, BlockPos outlet, ItemStack stack) {
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
                for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
                    ItemStack held = container.getItem(slot);
                    if (held.isEmpty()) {
                        container.setItem(slot, stack.copy());
                        stack.setCount(0);
                    } else if (ItemStack.isSameItemSameComponents(held, stack)) {
                        int room = held.getMaxStackSize() - held.getCount();
                        int moved = Math.min(room, stack.getCount());
                        if (moved > 0) {
                            held.grow(moved);
                            stack.shrink(moved);
                        }
                    }
                }
                container.setChanged();
            }
        }
        if (!stack.isEmpty()) {
            Block.popResource(level, outlet, stack);
        }
    }

    /**
     * Any removal - break, explosion, replace - hands the queue back. A machine that eats what it was
     * holding is worse than one that never accepted it.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
        super.preRemoveSideEffects(pos, oldState);
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        for (int slot = 0; slot < queue.size(); slot++) {
            ItemStack stack = queue.get(slot);
            if (!stack.isEmpty()) {
                Block.popResource(this.level, pos, stack.copy());
            }
            queue.set(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    private void stall(Level level, BlockPos pos, BlockState state) {
        setActive(level, pos, state, false);
    }

    /**
     * Flip the core's running state, and the bay's with it.
     *
     * <p>The bay cells have to be told separately: a dummy has no BlockEntity and no cheap way to find
     * its master while rendering, so it mirrors the flag instead. This was missed at first and the
     * symptom was the grinder never appearing to turn - the animated models existed and were simply
     * unreachable, because nothing ever put a cell into the state that selects them.
     *
     * <p>Guarded on an actual change so this does not write four blocks every tick.
     */
    private void setActive(Level level, BlockPos pos, BlockState state, boolean active) {
        if (!state.hasProperty(SeparatorCoreBlock.ACTIVE)
                || state.getValue(SeparatorCoreBlock.ACTIVE) == active) {
            return;
        }
        level.setBlock(pos, state.setValue(SeparatorCoreBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        for (BlockPos cell : SeparatorCoreBlock.chamberCells(level, pos)) {
            BlockState cellState = level.getBlockState(cell);
            if (cellState.hasProperty(SeparatorChamberBlock.ACTIVE)
                    && cellState.getValue(SeparatorChamberBlock.ACTIVE) != active) {
                level.setBlock(cell, cellState.setValue(SeparatorChamberBlock.ACTIVE, active),
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
        // Reset IN PLACE. NonNullList.withSize is fixed-size, so clear() and add() both throw - and the
        // throw aborts the whole of loadAdditional, which took the stored energy down with the queue.
        // The machine came back from a reload empty and cold, and the only sign was one line in a log.
        // Reset IN PLACE. NonNullList.withSize is fixed-size, so clear() and add() both throw - and the
        // throw aborts the whole of loadAdditional, which took the stored energy down with it. The
        // machine came back from a reload empty and cold, and the only sign was one line in a log.
        for (int slot = 0; slot < queue.size(); slot++) {
            queue.set(slot, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input, queue);
    }
}
