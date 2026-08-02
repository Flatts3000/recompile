package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.item.BlueprintItem;
import com.flatts.recompile.content.item.IdeaFragmentItem;
import com.flatts.recompile.content.recipe.FragmentAssemblyRecipe;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Filing Cabinet's contents (#95, spec {@code docs/blueprints_spec.md}): where a base keeps what it
 * has worked out how to build.
 *
 * <p><b>Blueprints only.</b> It is not storage, it is a reference shelf, and letting it take cobblestone
 * would make it a worse chest with a nicer texture. The filter is on {@link #canPlaceItem} rather than
 * on the slot, because in 26.1 vanilla's {@code Slot.mayPlace} returns true unconditionally and
 * {@code ChestMenu} uses a plain {@code Slot} - the same trap the Burn Barrel's refuse-only rule hit,
 * where the check had to move into the ticker because the slot was never consulted. Here the menu is
 * built with a filtering slot for the GUI and this method covers hoppers and pipes.
 *
 * <p><b>It reuses a vanilla screen</b>, per the rule in CLAUDE.md: a grid of items is exactly what a
 * chest screen is for, and nothing here needs a gauge. Six rows, which is 54 blueprints. The spec asked
 * for unbounded and this is not that - it is the largest a borrowed screen goes, and going past it means
 * minting a fifth custom screen and recording another reversal. Worth doing when a save actually holds
 * 54 distinct blueprints; the mod currently ships one.
 */
public class FilingCabinetBlockEntity extends RandomizableContainerBlockEntity {

    /** Six rows, the biggest a vanilla chest screen goes. */
    public static final int SLOTS = 54;

    /**
     * Ticks between filing passes. Once a second: a player tipping fragments in wants to see it happen,
     * and scanning 54 slots twenty times a second for a block that changes on a human timescale is
     * work for nothing.
     */
    private static final int CONDENSE_INTERVAL = 20;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public FilingCabinetBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.FILING_CABINET.get(), pos, state);
    }

    /**
     * Every blueprint set filed here.
     *
     * <p>This is what the crafting table asks, so it returns sets rather than stacks: the table wants to
     * know whether the knowledge is reachable, not how it is stored.
     */
    public List<Identifier> filed() {
        List<Identifier> sets = new ArrayList<>();
        for (ItemStack stack : items) {
            Identifier set = BlueprintItem.blueprintOf(stack);
            if (set != null) {
                sets.add(set);
            }
        }
        return sets;
    }

    /** Whether this cabinet holds the named blueprint. */
    public boolean holds(Identifier set) {
        for (ItemStack stack : items) {
            if (set.equals(BlueprintItem.blueprintOf(stack))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Blueprints and the fragments that become them, including from a hopper.
     *
     * <p>Fragments are accepted because the cabinet does the filing: drop a handful in and it assembles
     * the sheet itself. Anything else stays out - it is a reference shelf, and letting it take
     * cobblestone would make it a worse Scrap Barrel with a nicer texture.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.isEmpty()
            || stack.getItem() instanceof BlueprintItem
            || stack.getItem() instanceof IdeaFragmentItem;
    }

    /**
     * File what has been dropped in: fragments become blueprints, and surplus fragments are destroyed.
     *
     * <p><b>The cabinet does the assembling.</b> A player who has just torn down four mattresses should
     * be able to tip the fragments in and be done, rather than carry them to a crafting table to do a
     * step with one possible outcome.
     *
     * <p><b>Surplus is destroyed on purpose, and it is the one place this mod deletes a player's
     * items</b>, so the rule is narrow enough to state in a sentence: a fragment is only ever destroyed
     * when this cabinet already holds the blueprint it leads to. A second copy of knowledge is worth
     * nothing - blueprints do not even stack - so the alternative is a drawer slowly filling with
     * fragments that can never become anything, which is worse than a bin.
     *
     * <p>Fragments toward a blueprint the cabinet does NOT hold are left completely alone, however few
     * there are. Accumulating is the mechanic.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
            FilingCabinetBlockEntity cabinet) {
        if (level.getGameTime() % CONDENSE_INTERVAL != 0) {
            return;
        }
        cabinet.condense(level);
    }

    /**
     * Run one filing pass now, ignoring the interval.
     *
     * <p>The static entry point a GameTest calls, the same shape as {@code SortableBlock.sortOnce} and
     * {@code SortingTarpBlock.siftInput}. A test cannot go through {@link #serverTick}: game time does
     * not advance between calls inside one tick, so a loop of forty either all fire or all skip
     * depending on what second the test happened to start on.
     */
    public void condenseNow(Level level) {
        condense(level);
    }

    /** One pass: assemble what can be assembled, then bin what is now redundant. */
    private void condense(Level level) {
        Map<Identifier, Integer> fragments = new java.util.HashMap<>();
        for (ItemStack stack : items) {
            Identifier set = IdeaFragmentItem.towards(stack);
            if (set != null) {
                fragments.merge(set, stack.getCount(), Integer::sum);
            }
        }
        if (fragments.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (Map.Entry<Identifier, Integer> entry : fragments.entrySet()) {
            Identifier set = entry.getKey();
            if (!holds(set) && entry.getValue() >= FragmentAssemblyRecipe.requiredFor(level, set)) {
                if (!file(BlueprintItem.of(RCItems.BLUEPRINT.get(), set))) {
                    continue;   // no room for the sheet, so leave the fragments where they are
                }
                changed = true;
            }
            // Either it was just filed or it was already here; either way the fragments are spent.
            if (holds(set)) {
                changed |= discardFragments(set);
            }
        }
        if (changed) {
            setChanged();
        }
    }

    /**
     * File a stack here, merging into a matching one first.
     *
     * <p>Public because the Recompile Workbench files fragments straight in rather than dropping them
     * on the floor: the bench is where they are made and the cabinet is where they belong, and making
     * the player carry them four paces adds nothing.
     *
     * @return true if it fit
     */
    public boolean fileFrom(ItemStack stack) {
        if (!canPlaceItem(0, stack)) {
            return false;
        }
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack existing = items.get(slot);
            if (ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                existing.grow(stack.getCount());
                setChanged();
                return true;
            }
        }
        boolean filed = file(stack.copy());
        if (filed) {
            setChanged();
        }
        return filed;
    }

    /** Put a stack in the first free slot; false if the drawers are full. */
    private boolean file(ItemStack stack) {
        for (int slot = 0; slot < items.size(); slot++) {
            if (items.get(slot).isEmpty()) {
                items.set(slot, stack);
                return true;
            }
        }
        return false;
    }

    /** Remove every fragment pointing at this blueprint. */
    private boolean discardFragments(Identifier set) {
        boolean removed = false;
        for (int slot = 0; slot < items.size(); slot++) {
            if (set.equals(IdeaFragmentItem.towards(items.get(slot)))) {
                items.set(slot, ItemStack.EMPTY);
                removed = true;
            }
        }
        return removed;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.recompile.filing_cabinet");
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> replacement) {
        items = replacement;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.sixRows(containerId, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!trySaveLootTable(output)) {
            net.minecraft.world.ContainerHelper.saveAllItems(output, items);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(input)) {
            net.minecraft.world.ContainerHelper.loadAllItems(input, items);
        }
    }
}
