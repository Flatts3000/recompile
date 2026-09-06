package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.block.ScrapNetwork;
import com.flatts.recompile.content.freight.FreightManifest;
import com.flatts.recompile.content.menu.FreightTerminalMenu;
import com.flatts.recompile.content.menu.WideSync;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import com.flatts.recompile.content.freight.FreightPhases;
import com.flatts.recompile.content.freight.FreightState;
import com.flatts.recompile.content.recipe.FreightPhaseRecipe;
import com.flatts.recompile.registry.RCBlockEntities;
import java.util.Optional;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The Freight Terminal: goods in, tier out (#387, spec {@code docs/freight_conversion_spec.md}).
 *
 * <p><b>It takes pipe input, and that is the whole reason it is a separate block from the Sell
 * Terminal</b> (owner, 2026-09-06). Selling is a thing you walk up and do; freight is bulk and
 * sustained and is meant to be fed by a factory. The Sell Terminal has no block entity and no
 * container at all, so the distinction is mechanical rather than cosmetic and a player finds it by
 * trying to automate one. Hoppers, pipes and AE2 all reach it through the item capability.
 *
 * <p><b>The Scrap Network routes here, and it is the third sink</b> (#393). It is also the only
 * CONDITIONAL one: it claims a route solely for goods the current phase is waiting on, and only up to
 * the outstanding count, so freight can sit ahead of the bins without swallowing a player's sorted
 * materials. This javadoc has now said all three things - that the network routed here (#387, false),
 * that it did not (#392, true at the time), and that it does (#393) - which is why the claim is
 * stated against the code rather than against the intent.
 *
 * <p><b>Deliveries are consumed, not stored.</b> The slots are a landing strip: the ticker drains
 * them into {@link FreightState} and there is no way to get goods back out. That is Satisfactory's
 * behaviour and it avoids a chest-sized hole in the middle of the machine.
 *
 * <p><b>The safety is REFUSAL, not a buffer.</b> An item the current phase does not want is rejected
 * at the slot by {@link #canPlaceItem}, so a hopper backs up where the player can see it rather than
 * the terminal quietly eating a stack of something valuable. This mod fails closed and says why; the
 * vacuum naming a pile it cannot take is the same idea.
 *
 * <p><b>Nothing is taken out through a face, ever.</b> {@link #canTakeItemThroughFace} is false for
 * every slot, so a hopper under the terminal cannot pull a delivery back out mid-drain, which would
 * otherwise be a way to launder progress out of a phase.
 */
public class FreightTerminalBlockEntity extends BlockEntity
        implements WorldlyContainer, MenuProvider {

    /** A landing strip rather than a hold. Wide enough that one hopper never throttles a factory. */
    public static final int SLOT_COUNT = 9;

    private static final int[] ALL_SLOTS = new int[SLOT_COUNT];

    static {
        for (int i = 0; i < SLOT_COUNT; i++) {
            ALL_SLOTS[i] = i;
        }
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    /**
     * The current phase, resolved once a tick rather than once a question.
     *
     * <p><b>This is a real hot path, not a micro-optimisation.</b> {@link FreightPhases#sorted} scans
     * the recipe map, sorts it and copies the list twice, and without a cache that ran on every
     * hopper insert attempt via {@link #canPlaceItem}, and THIRTEEN TIMES A TICK per open screen via
     * the data slots - each of which also called {@link FreightPhases#length} for a second scan. One
     * open screen was twenty-six full scans a tick.
     *
     * <p>Refreshed unconditionally from {@link #serverTick}, so it is at most one tick stale and a
     * {@code /reload} is picked up on the next tick without any reload hook.
     */
    private FreightManifest cached = FreightManifest.NONE;

    /**
     * The current phase, UNTRUNCATED.
     *
     * <p><b>Admission must read this and never the manifest.</b> {@link FreightManifest} truncates to
     * {@code MAX_LINES} because that is what the screen can draw, and an earlier version of the cache
     * filtered {@link #canPlaceItem} off the manifest instead. A pack shipping a seven-line phase then
     * got a terminal that silently refused the seventh item while {@link #isSatisfied} still waited
     * for it: the hopper backs up, nothing is logged, and the ladder is stuck for good.
     */
    private FreightPhaseRecipe cachedPhase;

    /**
     * Whether {@link #cached} has ever been built.
     *
     * <p>A separate flag rather than testing {@code cached == NONE}, because NONE is a legitimate
     * RESULT - it is what a finished ladder looks like - and using it as "not yet computed" would
     * re-scan on every call for the rest of the save once the last phase landed.
     */
    private boolean primed;

    public FreightTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.FREIGHT_TERMINAL.get(), pos, state);
    }

    // ---- the drain -----------------------------------------------------------------------------

    /**
     * Move whatever the current phase wants out of the slots and into the world's progress.
     *
     * <p><b>The completion check re-reads the tier rather than trusting the one it captured</b>, via
     * {@link FreightState#completePhase(int)}. Two full slots draining on the same tick would
     * otherwise each see a satisfied phase and advance it twice, which is the acceptance criterion
     * this guard exists for.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
            FreightTerminalBlockEntity terminal) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        FreightState freight = FreightState.of(server);
        int tier = freight.tier();
        // The one place the ladder is actually scanned. Everything else reads the cache.
        terminal.refresh(server, tier);
        FreightPhaseRecipe phase = terminal.cachedPhase;
        if (phase == null) {
            return;      // the ladder is finished, or a pack shipped none
        }

        boolean changed = false;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = terminal.items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int wanted = phase.required(stack.getItem());
            if (wanted == 0) {
                continue;      // refused at the slot normally; this covers a /setblock or a reload
            }
            int outstanding = wanted - freight.delivered(stack.getItem());
            if (outstanding <= 0) {
                continue;      // that line is already satisfied, so leave the stack visible
            }
            int taken = Math.min(outstanding, stack.getCount());
            freight.deliver(stack.getItem(), taken);
            stack.shrink(taken);
            changed = true;
        }
        if (changed) {
            terminal.setChanged();
        }

        // ANYTHING THE PHASE NO LONGER WANTS GOES BACK OUT. Overshoot is possible from any door the
        // terminal does not control - a pipe or an AE2 export moving a whole stack against a boolean
        // canPlaceItem, or a phase completing while goods sit on the strip - and without this it
        // stranded in a block whose faces hand nothing back. Routed to the cluster rather than
        // dropped: the terminal refuses it itself, so it flows on to the bins and the barrel.
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack waiting = terminal.items.get(slot);
            if (waiting.isEmpty() || phase.required(waiting.getItem()) > 0) {
                continue;
            }
            ItemStack rest = ScrapNetwork.insertFromMember(server, pos, waiting, false);
            terminal.items.set(slot, rest);
            terminal.setChanged();
        }

        if (isSatisfied(freight, phase) && freight.completePhase(tier)) {
            FreightCompletion.onPhaseCompleted(server, pos, phase, tier + 1);
            // Immediately, not next tick: otherwise the strip would keep accepting the finished
            // phase's goods for a tick and the open screen would draw the old manifest.
            terminal.refresh(server, freight.tier());
        }
    }

    /**
     * How many more of {@code item} this terminal can usefully take, counting what is already on its
     * own strip.
     *
     * <p><b>A COUNT, not a boolean, and that distinction is the bug this replaced.</b>
     * {@link #canPlaceItem} answers "is any more wanted", which is right for a hopper because a hopper
     * moves one item per transfer. The Scrap Network moves a whole stack once the gate says yes, so a
     * 64 stack routed at a phase wanting 24 put all 64 on the strip and stranded 40 of them - the
     * exact "swallow a player's sorted materials" failure the conditional sink exists to prevent.
     */
    public int outstandingFor(Item item) {
        FreightPhaseRecipe phase = phase();
        if (phase == null || !(level instanceof ServerLevel server)) {
            return 0;
        }
        int wanted = phase.required(item);
        if (wanted == 0) {
            return 0;
        }
        int inbound = 0;
        for (ItemStack waiting : items) {
            if (waiting.getItem() == item) {
                inbound += waiting.getCount();
            }
        }
        return Math.max(0, wanted - FreightState.of(server).delivered(item) - inbound);
    }

    /**
     * Take up to {@code limit} from {@code stack} onto the strip, returning how many were taken.
     *
     * <p>Mutates {@code stack}. Used by the Scrap Network so routing can move exactly the outstanding
     * amount rather than a whole stack.
     */
    public int accept(ItemStack stack, int limit) {
        int budget = Math.min(limit, stack.getCount());
        int taken = 0;
        for (int slot = 0; slot < SLOT_COUNT && budget > 0; slot++) {
            ItemStack existing = items.get(slot);
            if (existing.isEmpty()) {
                int move = Math.min(budget, stack.getMaxStackSize());
                items.set(slot, stack.split(move));
                taken += move;
                budget -= move;
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int move = Math.min(budget, existing.getMaxStackSize() - existing.getCount());
                if (move > 0) {
                    existing.grow(move);
                    stack.shrink(move);
                    taken += move;
                    budget -= move;
                }
            }
        }
        if (taken > 0) {
            setChanged();
        }
        return taken;
    }

    /** Whether every line of {@code phase} has been delivered. */
    public static boolean isSatisfied(FreightState freight, FreightPhaseRecipe phase) {
        for (FreightPhaseRecipe.Requirement requirement : phase.requires()) {
            if (freight.delivered(requirement.item()) < requirement.count()) {
                return false;
            }
        }
        return true;
    }

    // ---- the container -------------------------------------------------------------------------

    /** Only what the current phase still wants. Everything else backs the pipe up. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        // Off the cache, and off the PHASE rather than the manifest: the manifest is truncated for
        // the screen and admission must see every line. A hopper asks this on every insert attempt,
        // so a recipe-map scan here would cost a scan per hopper per tick.
        FreightPhaseRecipe phase = phase();
        if (phase == null) {
            return false;
        }
        int wanted = phase.required(stack.getItem());
        if (wanted == 0) {
            return false;
        }
        // Count what is already waiting on the strip, not just what has been delivered. Without this
        // a bank of pipes fills all nine slots on the tick before the last few are taken, and the
        // remainder is stranded in a block that hands nothing back through a face.
        int inbound = 0;
        for (ItemStack waiting : items) {
            if (waiting.getItem() == stack.getItem()) {
                inbound += waiting.getCount();
            }
        }
        return FreightState.of(server).delivered(stack.getItem()) + inbound < wanted;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return ALL_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    /** Nothing comes back out. A delivery is spent the moment it lands. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.recompile.freight_terminal");
    }

    /**
     * The manifest the screen draws, resolved server-side and sent in the open buffer.
     *
     * <p>Requirements do not change while a screen is open, so they travel once here rather than
     * every tick through the 16-bit data channel - where a count of 100,000 would not fit anyway.
     */
    public FreightManifest manifest() {
        if (level instanceof ServerLevel server && !primed) {
            // Lazily populate for the window between placement and the first tick, and for a screen
            // opened in that window.
            refresh(server, FreightState.of(server).tier());
        }
        return cached;
    }

    /** Rebuild the cached manifest. The only caller that scans the ladder. */
    private void refresh(ServerLevel server, int tier) {
        // ONE scan. The first version called FreightPhases.current and FreightPhases.length here and
        // then current() again from the ticker - three scans a tick, in the commit that claimed to
        // have reduced it to one.
        List<FreightPhaseRecipe> ladder = FreightPhases.sorted(server);
        cachedPhase = tier >= 0 && tier < ladder.size() ? ladder.get(tier) : null;
        cached = cachedPhase == null
            ? FreightManifest.NONE
            : FreightManifest.of(cachedPhase, tier, ladder.size());
        primed = true;
    }

    /** The untruncated phase behind {@link #manifest()}, or null when the ladder is done. */
    public @Nullable FreightPhaseRecipe phase() {
        if (level instanceof ServerLevel server && !primed) {
            refresh(server, FreightState.of(server).tier());
        }
        return cachedPhase;
    }

    /** Delivered counts as low/high pairs, then the tier. See {@code FreightTerminalMenu}. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (!(level instanceof ServerLevel server)) {
                return 0;
            }
            FreightState freight = FreightState.of(server);
            if (index == FreightTerminalMenu.TIER_INDEX) {
                return freight.tier();
            }
            int line = index / 2;
            FreightManifest current = manifest();
            if (line >= current.lines().size()) {
                return 0;
            }
            int delivered = freight.delivered(current.lines().get(line).item());
            return index % 2 == 0 ? WideSync.low(delivered) : WideSync.high(delivered);
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative: the client never writes progress.
        }

        @Override
        public int getCount() {
            return FreightTerminalMenu.DATA_SIZE;
        }
    };

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FreightTerminalMenu(containerId, inventory, this, data, manifest());
    }

    // ---- persistence ---------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
    }
}
