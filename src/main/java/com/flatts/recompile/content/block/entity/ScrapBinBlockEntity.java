package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.ScrapBinContents;
import com.flatts.recompile.content.block.ScrapBinBlock;
import com.flatts.recompile.content.block.ScrapBinContent;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCDataComponents;
import com.flatts.recompile.registry.RCTags;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * The Scrap Bin's contents (design P2.9): a single salvage type and how much of it, up to a large
 * configurable cap.
 *
 * <p><b>Not a vanilla {@link net.minecraft.world.Container}, on purpose.</b> A Container caps a slot
 * at a stack and scatters its contents on break; this bin holds thousands and must carry them onto
 * its dropped item instead. So it stores a bound {@link Item} plus an {@code int} amount directly,
 * and exposes a hand-rolled {@link ResourceHandler}&lt;{@link ItemResource}&gt; for automation. That
 * handler moves items <b>both ways</b> (owner call, 2026-07-31): a pipe or sorter fills a bin, and a
 * pipe pulls the stockpile back out. This reverses P2.9's original "hopper in, no automation out", which
 * made a wall of bins a place materials went to die.
 *
 * <p><b>The binding outlives an empty bin.</b> Withdrawing the last item leaves {@link #boundMaterial}
 * set, so a placed bin stays bound and refills without re-binding. Only breaking it while empty clears
 * the binding: the drop component is written only when something is stored (see
 * {@link #collectImplicitComponents}), so an empty bin drops blank and a full one drops loaded - the
 * Rain Collector's break-survives pattern.
 */
public class ScrapBinBlockEntity extends BlockEntity {

    /** Right-clicks closer together than this (ticks) count as a double - deposit everything. */
    private static final long DOUBLE_CLICK_TICKS = 8;

    @Nullable
    private Item boundMaterial;
    private int amount;

    // Transient double-click state (not serialized) - see rightClickIsDouble.
    @Nullable
    private UUID lastClicker;
    private long lastClickTick = Long.MIN_VALUE;

    private final Storage storage = new Storage();
    private final ContentsJournal journal = new ContentsJournal();

    public ScrapBinBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.SCRAP_BIN.get(), worldPosition, blockState);
    }

    private static int capacity() {
        return RCConfig.SCRAP_BIN_CAPACITY.get();
    }

    private static boolean isBinnable(Item item) {
        return item.builtInRegistryHolder().is(RCTags.BINNABLE);
    }

    /** The automation view: insert works (gated), extract never does. Handed to hoppers by side. */
    public ResourceHandler<ItemResource> storageHandler() {
        return storage;
    }

    // ---------------- manual interaction (screen-free) ----------------

    /**
     * Deposit from a held stack, binding the bin if it is empty. Returns how many were taken, so the
     * caller can shrink the player's stack. Routed through the same transactional insert a hopper
     * uses, so binding and capacity behave identically by hand or by machine.
     */
    public int deposit(ItemStack held) {
        if (held.isEmpty()) {
            return 0;
        }
        int accepted;
        try (Transaction transaction = Transaction.openRoot()) {
            accepted = storage.insert(ItemResource.of(held), held.getCount(), transaction);
            if (accepted > 0) {
                transaction.commit();
            }
        }
        if (accepted > 0) {
            held.shrink(accepted);
        }
        return accepted;
    }

    /**
     * Withdraw a stack (or a single item), keeping the binding even if this empties the bin. Bypasses
     * the handler's blocked {@link Storage#extract} - that block exists only to stop <em>automation</em>
     * pulling; a player's hands are the intended way out.
     */
    public ItemStack withdraw(boolean single) {
        if (boundMaterial == null || amount == 0) {
            return ItemStack.EMPTY;
        }
        int stackMax = new ItemStack(boundMaterial).getMaxStackSize();
        return withdraw(single ? 1 : stackMax);
    }

    /**
     * Take up to {@code max} of the bound material.
     *
     * <p>The panel's per-click quantities (one / a stack / half, issue #86) need an arbitrary amount, not
     * the one-or-stack the boolean form offers. That form now delegates here.
     */
    public ItemStack withdraw(int max) {
        if (boundMaterial == null || amount == 0 || max <= 0) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(amount, max);
        amount -= taken;
        ItemStack out = new ItemStack(boundMaterial, taken);
        afterContentsChanged();
        return out;
    }

    public boolean isEmpty() {
        return amount == 0;
    }

    /** Whether this bin would take the stack: binnable, and (once bound) matching the bound material. */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return isBinnable(item) && (boundMaterial == null || boundMaterial == item);
    }

    /**
     * Functional Storage's double-click-to-deposit-all: a right-click within a short window of the
     * same player's last one is a double-click. Transient (not serialized) - it is momentary UI state.
     * The window is consumed on a double so three fast clicks are not two doubles.
     */
    public boolean rightClickIsDouble(UUID player, long gameTime) {
        boolean isDouble = player.equals(lastClicker) && gameTime - lastClickTick <= DOUBLE_CLICK_TICKS;
        if (isDouble) {
            lastClicker = null;
            lastClickTick = Long.MIN_VALUE;
        } else {
            lastClicker = player;
            lastClickTick = gameTime;
        }
        return isDouble;
    }

    @Nullable
    public Item boundMaterial() {
        return boundMaterial;
    }

    public int amount() {
        return amount;
    }

    public int capacityForDisplay() {
        return capacity();
    }

    // ---------------- state sync ----------------

    /** Push the current content + fill onto the blockstate (drives the color handler and Jade). */
    private void syncState() {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof ScrapBinBlock)) {
            return;
        }
        ScrapBinContent content = boundMaterial == null
            ? ScrapBinContent.EMPTY : ScrapBinContent.forItem(boundMaterial);
        BlockState updated = state
            .setValue(ScrapBinBlock.CONTENT, content)
            .setValue(ScrapBinBlock.FILL, fillLevel());
        if (updated != state) {
            level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
        }
    }

    /** 0 when empty, else 1..4 by proportion - any positive amount shows at least one level. */
    private int fillLevel() {
        if (amount <= 0) {
            return 0;
        }
        int level = (int) Math.ceil((double) amount / capacity() * 4.0);
        return Math.max(1, Math.min(4, level));
    }

    private void afterContentsChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            syncState();
        }
    }

    // ---------------- persistence ----------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (boundMaterial != null) {
            output.store("material", BuiltInRegistries.ITEM.byNameCodec(), boundMaterial);
        }
        output.putInt("amount", amount);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        boundMaterial = input.read("material", BuiltInRegistries.ITEM.byNameCodec()).orElse(null);
        amount = input.getIntOr("amount", 0);
    }

    // ---------------- carry contents through break + replace (item data component) ----------------

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (boundMaterial != null && amount > 0) {
            builder.set(RCDataComponents.SCRAP_BIN_CONTENTS.get(),
                new ScrapBinContents(boundMaterial, amount));
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter getter) {
        super.applyImplicitComponents(getter);
        ScrapBinContents contents = getter.get(RCDataComponents.SCRAP_BIN_CONTENTS.get());
        if (contents != null && contents.count() > 0) {
            boundMaterial = contents.material();
            amount = contents.count();
        }
        // Placement runs this before the BE is fully in-world; sync happens in setPlacedBy.
    }

    /** Called by the block after placement so a restored bin shows its content and fill at once. */
    public void refreshStateAfterPlacement() {
        if (level != null && !level.isClientSide()) {
            syncState();
        }
    }

    // ---------------- the automation handler (in and out) ----------------

    private final class Storage implements ResourceHandler<ItemResource> {

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            return (boundMaterial == null || amount == 0) ? ItemResource.EMPTY : ItemResource.of(boundMaterial);
        }

        @Override
        public long getAmountAsLong(int index) {
            return amount;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return capacity();
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return accepts(resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int inserted, TransactionContext transaction) {
            if (index != 0 || inserted <= 0 || resource.isEmpty() || !accepts(resource)) {
                return 0;
            }
            int accepted = Math.min(inserted, capacity() - amount);
            if (accepted <= 0) {
                return 0;
            }
            journal.updateSnapshots(transaction);
            if (boundMaterial == null) {
                boundMaterial = resource.getItem();
            }
            amount += accepted;
            return accepted;
        }

        /**
         * Pipes take from the bin as well as fill it (owner call, 2026-07-31, reversing P2.9's
         * "hopper in, no out").
         *
         * <p>The bin stays <b>bound</b> through an empty: extracting to zero leaves {@code boundMaterial}
         * set, so a pipe draining a bin does not silently un-type it and let the next unrelated insert
         * re-bind it to something else. Un-binding remains a deliberate player action.
         */
        @Override
        public int extract(int index, ItemResource resource, int extracted, TransactionContext transaction) {
            if (index != 0 || extracted <= 0 || resource.isEmpty()
                    || boundMaterial == null || resource.getItem() != boundMaterial) {
                return 0;
            }
            int taken = Math.min(extracted, amount);
            if (taken <= 0) {
                return 0;
            }
            journal.updateSnapshots(transaction);
            amount -= taken;
            return taken;
        }

        /** Accept only binnable salvage, and once bound only the bound material. */
        private boolean accepts(ItemResource resource) {
            Item item = resource.getItem();
            return isBinnable(item) && (boundMaterial == null || boundMaterial == item);
        }
    }

    /**
     * Makes the {boundMaterial, amount} pair transaction-safe: a hopper opens a transaction, inserts,
     * then may roll back (its source could not supply). Without journaling, a rollback would leave the
     * bin holding items that were never removed from the source - a dupe. The journal snapshots both
     * fields before a mutation and restores them if the transaction aborts.
     */
    private final class ContentsJournal extends SnapshotJournal<Snapshot> {

        @Override
        protected Snapshot createSnapshot() {
            return new Snapshot(boundMaterial, amount);
        }

        @Override
        protected void revertToSnapshot(Snapshot snapshot) {
            boundMaterial = snapshot.material();
            amount = snapshot.amount();
        }

        @Override
        protected void onRootCommit(Snapshot snapshot) {
            afterContentsChanged();
        }
    }

    private record Snapshot(@Nullable Item material, int amount) {
    }
}
