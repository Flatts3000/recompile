package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.block.PulverizerCoreBlock;
import com.flatts.recompile.content.block.ScrapNetwork;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.content.recipe.PulverizingRecipe;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCRecipeTypes;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
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
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Pulverizer's brain: one recipe at a time, fed from above, discharging out the front.
 *
 * <p><b>No inventory, on the Separator's terms.</b> It is not a {@code Container} and exposes no item
 * capability, so nothing can push into it and no pipe can connect. It automates by reaching out: it
 * swallows loose items landing on its roof and drains a container parked there. Reaching out and being
 * reached into are different doors, and only the second is shut.
 *
 * <p><b>The count is the ratio dial.</b> A recipe consuming several of its input is how a chain gets
 * tuned without touching a drop weight that feeds other things - see {@link PulverizingRecipe}. The
 * machine is the only thing that knows how much material is in front of it, so the recipe declares the
 * count and this checks it.
 */
public class PulverizerBlockEntity extends BlockEntity {

    private static final int BUFFER = 6000;

    public static final int QUEUE_SLOTS = 9;

    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(BUFFER, BUFFER, BUFFER);

    private final NonNullList<ItemStack> queue = NonNullList.withSize(QUEUE_SLOTS, ItemStack.EMPTY);

    private int progress;

    private int goal;

    /** How much of the head item the current recipe wants, so Jade can say "3 of 8". */
    private int feedHave;

    private int feedNeed;

    /** What the head slot held when this run started, so a swap cannot bank one item's progress. */
    private ItemStack milling = ItemStack.EMPTY;

    public PulverizerBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.PULVERIZER.get(), pos, state);
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

    public int feedHave() {
        return feedHave;
    }

    public int feedNeed() {
        return feedNeed;
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
                                  PulverizerBlockEntity be) {
        if (!MultiblockCoreBlock.isFormed(state)) {
            be.stall(level, pos, state);
            return;
        }

        be.intake(level, pos);

        int head = be.headSlot(level);
        if (head < 0) {
            be.progress = 0;
            be.goal = 0;
            be.feedHave = 0;
            be.feedNeed = 0;
            be.milling = ItemStack.EMPTY;
            be.stall(level, pos, state);
            return;
        }
        ItemStack stack = be.queue.get(head);
        RecipeHolder<PulverizingRecipe> match = be.recipeFor(level, stack);
        if (match == null) {
            be.stall(level, pos, state);
            return;
        }

        // A different item at the head means a different run. Banking one item's progress against
        // another would let a player start a slow mill and finish it with something cheap.
        if (!ItemStack.isSameItem(be.milling, stack)) {
            be.milling = stack.copy();
            be.progress = 0;
        }
        be.goal = match.value().ticks();
        be.feedHave = stack.getCount();
        be.feedNeed = match.value().count();
        if (stack.getCount() < be.feedNeed) {
            // Not enough to mill yet. Hold, do not consume - a machine that ate a partial stack and
            // gave nothing back would be indistinguishable from one that lost it.
            be.stall(level, pos, state);
            return;
        }

        try (Transaction tx = Transaction.openRoot()) {
            if (be.battery.extract(match.value().energy(), tx) < match.value().energy()) {
                be.stall(level, pos, state);
                return;
            }
            tx.commit();
        }

        be.setActive(level, pos, state, true);
        be.progress++;
        if (be.progress >= match.value().ticks()) {
            be.progress = 0;
            be.mill(level, pos, match.value(), head);
        }
        be.setChanged();
    }

    private @org.jetbrains.annotations.Nullable RecipeHolder<PulverizingRecipe> recipeFor(
            ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Optional<RecipeHolder<PulverizingRecipe>> found = level.recipeAccess()
            .getRecipeFor(RCRecipeTypes.PULVERIZING.get(), new SingleRecipeInput(stack), level);
        return found.orElse(null);
    }

    /**
     * The first slot holding something this machine can mill, or -1.
     *
     * <p>Also the self-heal: anything queued that no longer has a recipe leaves by the discharge rather
     * than blocking the head forever. That can only happen when a datapack changes under a saved world,
     * and a machine bricked by a pack update is a far worse outcome than an item on the floor.
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
            deliverToChute(level, PulverizerCoreBlock.outlet(level, worldPosition),
                PulverizerCoreBlock.dischargeFacing(level, worldPosition).getOpposite(), stack.copy());
            queue.set(slot, ItemStack.EMPTY);
            setChanged();
        }
        return -1;
    }

    private boolean accepts(ServerLevel level, ItemStack stack) {
        return recipeFor(level, stack) != null;
    }

    /** Swallow loose items landing on the roof, and drain a container parked there. */
    private void intake(ServerLevel level, BlockPos pos) {
        AABB mouth = PulverizerCoreBlock.mouth(level, pos);
        if (mouth == null) {
            return;
        }
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, mouth)) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || !accepts(level, stack)) {
                continue;   // nothing to do with it: leave it lying rather than swallow it
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

        // A container parked on the roof is drained, the way the Separator drains one on its bay and
        // the Trommel drains one on its drum. This is the route that runs without the player.
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                BlockPos above = new BlockPos(
                    (int) Math.floor(mouth.minX) + x, (int) Math.floor(mouth.minY),
                    (int) Math.floor(mouth.minZ) + z);
                Container container = HopperBlockEntity.getContainerAt(level, above);
                if (container == null) {
                    continue;
                }
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (stack.isEmpty() || !accepts(level, stack)) {
                        continue;
                    }
                    // One slot's worth per tick, so the mill sips from a chest instead of vacuuming it.
                    ItemStack left = insert(stack);
                    if (left.getCount() != stack.getCount()) {
                        container.setItem(slot, left.isEmpty() ? ItemStack.EMPTY : left);
                        container.setChanged();
                    }
                    break;
                }
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

    /** Consume the recipe's count and produce its one finer output. */
    private void mill(ServerLevel level, BlockPos pos, PulverizingRecipe recipe, int slot) {
        ItemStack stack = queue.get(slot);
        if (stack.getCount() < recipe.count()) {
            return;
        }
        stack.shrink(recipe.count());
        if (stack.isEmpty()) {
            queue.set(slot, ItemStack.EMPTY);
            milling = ItemStack.EMPTY;
        }

        BlockPos outlet = PulverizerCoreBlock.outlet(level, pos);
        Direction entry = PulverizerCoreBlock.dischargeFacing(level, pos).getOpposite();
        deliver(level, pos, outlet, entry, recipe.result().toStack());
        // A hammer mill is the loudest thing in a recycling plant. Anvil rather than gravel, because
        // this is impact and the other two machines already own the tearing and the rattling.
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 1.6F);
        setChanged();
    }

    private static void deliver(ServerLevel level, BlockPos member, BlockPos outlet, Direction entry,
                                ItemStack stack) {
        // The Scrap Network first, the way every other machine that produces routes what it makes.
        // autoBind is false: a mill run must never surprise-bind an empty bin.
        ItemStack remainder = ScrapNetwork.insertFromMember(level, member, stack, false);
        if (remainder.isEmpty()) {
            return;
        }
        deliverToChute(level, outlet, entry, remainder);
    }

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
                // Vanilla's own helper, not a hand-rolled slot loop: it honours canPlaceItem and, for a
                // WorldlyContainer, getSlotsForFace - which is what keeps a route out of the Burn
                // Barrel's smelt slots.
                stack = HopperBlockEntity.addItem(null, container, stack, entry);
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

    private void setActive(Level level, BlockPos pos, BlockState state, boolean active) {
        if (!state.hasProperty(PulverizerCoreBlock.ACTIVE)
                || state.getValue(PulverizerCoreBlock.ACTIVE) == active) {
            return;
        }
        level.setBlock(pos, state.setValue(PulverizerCoreBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
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
        // with the queue.
        for (int slot = 0; slot < queue.size(); slot++) {
            queue.set(slot, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input, queue);
        // Restore `milling` from the queue so saved progress is not thrown away on the first tick after
        // a reload - the same defect the Trommel had.
        milling = ItemStack.EMPTY;
        for (ItemStack stack : queue) {
            if (!stack.isEmpty()) {
                milling = stack.copy();
                break;
            }
        }
    }
}
