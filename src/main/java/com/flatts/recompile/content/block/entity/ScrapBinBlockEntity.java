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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
 * handler is <b>insert-only</b> - {@link Storage#extract} always returns zero - which is the design's
 * "hopper in, no automation out": a sorter can fill a bin, nothing pulls from it, and you spend the
 * stockpile by hand.
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
        int taken = Math.min(amount, single ? 1 : stackMax);
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
            // The FILL blockstate only moves per quartile, but the crafting-station panel shows the
            // exact count, so push a BlockEntity data packet on every change to keep it live.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---------------- client sync (for the crafting-station panel) ----------------

    /** Sync the bound material + amount to the client so the Scrap Crafting Table panel can show them. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
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

    // ---------------- the automation handler (insert-only) ----------------

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

        /** No automation out - the bin is a sink. A player withdraws by hand via {@link #withdraw}. */
        @Override
        public int extract(int index, ItemResource resource, int extracted, TransactionContext transaction) {
            return 0;
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
