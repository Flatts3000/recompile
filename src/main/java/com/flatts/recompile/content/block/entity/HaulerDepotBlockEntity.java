package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.ScrapNetwork;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.entity.ScrapHaulerEntity;
import com.flatts.recompile.content.item.ScrapHaulerItem;
import com.flatts.recompile.content.menu.HaulerDepotMenu;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCEntities;
import com.flatts.recompile.registry.RCSounds;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

/**
 * The Hauler Depot's state (#376, spec {@code docs/scrap_hauler_spec.md}): one Scrap Hauler, a hold
 * for what it brings back, and a buffer the generators fill.
 *
 * <p><b>Close to "a Charging Station that also deploys a Hauler and holds cargo"</b>, and built that
 * way on purpose. The charging loop is the station's whole: an insert-only {@link SimpleEnergyHandler}
 * so a Solar Panel or a Burner against any face fills it, pushed into the docked item through
 * {@code Capabilities.Energy.ITEM}. What the station does not have is the hold, the deploy, and the
 * network membership - and each of those is the reason this is its own block.
 *
 * <p><b>The conservation invariant lives here.</b> The Hauler exists exactly once: as the item in
 * slot 0, or as the entity this deployed. While deployed the slot is LOCKED - {@link #canPlaceItem}
 * refuses a second one, the menu's slot refuses pickup, the faces refuse extraction - and the
 * entity's {@code remove} calls {@link #onHaulerGone} so a machine that ceases to exist by any route
 * unlocks it. {@link #deploy} re-derives its own preconditions rather than trusting the button that
 * asked, because a button is an integer id on the wire and a laggy double-click sends it twice.
 *
 * <p><b>RF is optional</b> (ruling 8). The docked item also trickles from sky light when the Depot
 * can see it, so a Depot with nothing wired to it still turns its Hauler around, just slowly. Power is
 * a speed-up rather than a gate, which is what keeps the feature reachable before the power tier.
 *
 * <p><b>The hold is a surge tank</b> (rulings 9 and 10). The Depot pushes into the Scrap Network
 * continuously and the inventory exists because the Hauler gathers far faster than a Trommel eats;
 * it fills only when downstream is backed up. Being in the network is a TAG, and this block's
 * membership in {@code #recompile:scrap_connectable} is part of the block, not a decoration on it:
 * {@code ScrapNetwork.collect} returns nothing when the block it floods from is not a member, and the
 * Slag Furnace shipped exactly that silent no-op once.
 */
public class HaulerDepotBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int HAULER_SLOT = 0;
    public static final int CARGO_START = 1;
    public static final int CARGO_SLOTS = 27;
    public static final int SLOT_COUNT = CARGO_START + CARGO_SLOTS;

    /** The Charging Station's, and under the 16-bit wire ceiling: one data slot carries it. */
    public static final int CAPACITY = 20_000;
    public static final int TRANSFER_PER_TICK = 200;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_DEPLOYED = 1;
    public static final int DATA_HAULER_CHARGE = 2;
    public static final int DATA_MODE = 3;
    public static final int DATA_CARGO = 4;
    public static final int DATA_RADIUS = 5;
    /**
     * The configured ceiling, SYNCED rather than read off the client's own config.
     *
     * <p>{@code RCConfig} is COMMON and NeoForge does not sync those, so a client whose file differs
     * from the server's greys the plus button at the wrong number: too low and the player cannot reach
     * an area the server would allow, too high and the button does nothing when clicked because the
     * server clamps and the readout never moves. This is the same mistake #369 fixed on the Hydroponics
     * Bay, made again three files away, which is why the Bay's javadoc now says so out loud.
     */
    public static final int DATA_MAX_RADIUS = 6;
    public static final int DATA_SIZE = 7;

    /** A new Depot works its own chunk and the ring around it. */
    public static final int DEFAULT_CHUNK_RADIUS = 1;

    private static final int[] CARGO_FACES;

    static {
        CARGO_FACES = new int[CARGO_SLOTS];
        for (int i = 0; i < CARGO_SLOTS; i++) {
            CARGO_FACES[i] = CARGO_START + i;
        }
    }

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(CAPACITY, CAPACITY, CAPACITY);
    private int savedEnergy;

    private int chunkRadius = DEFAULT_CHUNK_RADIUS;
    private boolean deployed;
    private @Nullable UUID haulerUuid;
    private boolean recallRequested;
    /** What the deployed entity last reported, for the screen and Jade while it is out. */
    private int fieldCharge;
    private int fieldMode;
    private int fieldCargo;
    private int pushCursor;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> battery.getAmountAsInt();
                case DATA_DEPLOYED -> deployed ? 1 : 0;
                case DATA_HAULER_CHARGE -> deployed ? fieldCharge : ScrapHaulerItem.charge(items.get(HAULER_SLOT));
                case DATA_MODE -> deployed ? fieldMode : -1;
                case DATA_CARGO -> deployed ? fieldCargo : 0;
                case DATA_RADIUS -> chunkRadius();
                case DATA_MAX_RADIUS -> maxChunkRadius();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative; the client never writes back.
        }

        @Override
        public int getCount() {
            return DATA_SIZE;
        }
    };

    public HaulerDepotBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.HAULER_DEPOT.get(), pos, state);
    }

    public SimpleEnergyHandler battery() {
        return battery;
    }

    public int stored() {
        return battery.getAmountAsInt();
    }

    public boolean deployed() {
        return deployed;
    }

    public boolean recallRequested() {
        return recallRequested;
    }

    /**
     * The work area, as a chunk radius around this block (owner, 2026-09-05): 0 is this chunk, 1 is
     * 3x3, 2 is 5x5. Set from the screen, clamped to {@link #maxChunkRadius} on every read so a
     * lowered config ceiling takes effect on existing Depots without a migration.
     */
    public int chunkRadius() {
        return Math.min(chunkRadius, maxChunkRadius());
    }

    public static int maxChunkRadius() {
        return RCConfig.HAULER_MAX_CHUNK_RADIUS.get();
    }

    /** The screen's plus and minus. Clamped here, on the server, whatever the button believed. */
    public void adjustRadius(int delta) {
        int next = Mth.clamp(chunkRadius() + delta, 0, maxChunkRadius());
        if (next != chunkRadius) {
            chunkRadius = next;
            setChanged();
        }
    }

    /**
     * Whether {@code uuid} is THE Hauler this Depot deployed.
     *
     * <p>Found live rather than by reasoning: a Depot broken while its Hauler was somewhere unloaded
     * drops the Hauler item, and if a NEW Depot is then placed on the same coordinates before the old
     * entity loads, that entity finds a Depot at its home position, adopts it, and works for it. The
     * new Depot then has two machines in the field and one item in its slot, which is the duplication
     * the invariant exists to prevent - measured as "hauler entity present: Count: 2" in a dev client
     * that rebuilt its stage on the same spot. The entity asks this every tick and folds if the answer
     * is no.
     */
    public boolean owns(UUID uuid) {
        return deployed && uuid.equals(haulerUuid);
    }

    public boolean hasHauler() {
        return items.get(HAULER_SLOT).getItem() instanceof ScrapHaulerItem;
    }

    public ItemStack hauler() {
        return items.get(HAULER_SLOT);
    }

    public ContainerData data() {
        return data;
    }

    public int fieldCharge() {
        return fieldCharge;
    }

    public int fieldCargo() {
        return fieldCargo;
    }

    public int fieldMode() {
        return fieldMode;
    }

    // ---- ticking -------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, HaulerDepotBlockEntity depot) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        depot.chargeDocked();
        depot.trickleDocked(server);
        depot.pushOne(server);
        depot.watchField(server);
        depot.markBufferDirty();
    }

    private void chargeDocked() {
        ItemStack stack = items.get(HAULER_SLOT);
        if (deployed || stack.isEmpty() || battery.getAmountAsInt() <= 0
                || ScrapHaulerItem.charge(stack) >= ScrapHaulerItem.capacityOf(stack)) {
            return;
        }
        EnergyHandler target = ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
        if (target == null) {
            return;
        }
        int moved;
        try (Transaction transaction = Transaction.openRoot()) {
            moved = EnergyHandlerUtil.move(battery, target, TRANSFER_PER_TICK, transaction);
            transaction.commit();
        }
        if (moved > 0) {
            setChanged();
        }
    }

    /** RF is optional: a docked Hauler under open sky charges the way it does in the field. */
    private void trickleDocked(ServerLevel level) {
        ItemStack stack = items.get(HAULER_SLOT);
        if (deployed || !(stack.getItem() instanceof ScrapHaulerItem)
                || ScrapHaulerItem.charge(stack) >= ScrapHaulerItem.CAPACITY) {
            return;
        }
        BlockPos above = worldPosition.above();
        if (!level.canSeeSky(above)) {
            return;
        }
        int daylight = level.getBrightness(LightLayer.SKY, above) - level.getSkyDarken();
        if (daylight >= 12) {
            ScrapHaulerItem.setCharge(stack, ScrapHaulerItem.charge(stack) + ScrapHaulerEntity.SOLAR_PER_TICK);
            setChanged();
        }
    }

    /** Continuous push, one slot per tick, round-robin so a stuck stack cannot starve the others. */
    private void pushOne(ServerLevel level) {
        for (int n = 0; n < CARGO_SLOTS; n++) {
            // Wrapped rather than left to grow. It was `pushCursor++ % CARGO_SLOTS`, and the counter
            // ticks 27 times a second per Depot even when every slot is empty - about 46 days of
            // continuous ticking to overflow, which a spawn-chunk Depot on a long-lived server reaches.
            // A negative int modulo is negative in Java, so slot went to -10 and the server tick threw.
            pushCursor = (pushCursor + 1) % CARGO_SLOTS;
            int slot = CARGO_START + pushCursor;
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = ScrapNetwork.insertFromMember(level, worldPosition, stack.copy(), false);
            if (rest.getCount() != stack.getCount()) {
                items.set(slot, rest);
                setChanged();
            }
            return;
        }
    }

    /** Keep the screen's picture of the field current while the Hauler is out. */
    private void watchField(ServerLevel level) {
        if (!deployed || haulerUuid == null) {
            return;
        }
        if (level.getEntity(haulerUuid) instanceof ScrapHaulerEntity hauler) {
            fieldCharge = hauler.charge();
            fieldMode = hauler.mode().ordinal();
            fieldCargo = hauler.cargoCount();
        }
    }

    private void markBufferDirty() {
        int now = battery.getAmountAsInt();
        if (now != savedEnergy) {
            savedEnergy = now;
            setChanged();
        }
    }

    // ---- deploy and recall --------------------------------------------------------------------

    /**
     * Let the Hauler out. Refuses unless there is one in the slot and none in the world, whatever the
     * caller believed - that is the sixth row of the conservation table.
     */
    public boolean deploy(ServerLevel level) {
        ItemStack stack = items.get(HAULER_SLOT);
        if (deployed || !(stack.getItem() instanceof ScrapHaulerItem)) {
            return false;
        }
        BlockPos at = worldPosition.above();
        ScrapHaulerEntity hauler = RCEntities.SCRAP_HAULER.get().create(level, EntitySpawnReason.TRIGGERED);
        if (hauler == null) {
            return false;
        }
        hauler.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
        hauler.bind(worldPosition);
        hauler.setCharge(ScrapHaulerItem.charge(stack));
        hauler.setMode(ScrapHaulerEntity.Mode.SEEKING);
        if (!level.addFreshEntity(hauler)) {
            return false;
        }
        deployed = true;
        haulerUuid = hauler.getUUID();
        recallRequested = false;
        fieldCharge = hauler.charge();
        fieldMode = hauler.mode().ordinal();
        fieldCargo = 0;
        level.playSound(null, worldPosition, RCSounds.HAULER_DEPLOY.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        setChanged();
        return true;
    }

    /**
     * Bring it home. If the entity is loaded this happens now; if it is somewhere unloaded, the
     * request is remembered and the entity acts on it the next time it ticks.
     */
    public boolean recall(ServerLevel level) {
        if (!deployed) {
            return false;
        }
        if (haulerUuid != null && level.getEntity(haulerUuid) instanceof ScrapHaulerEntity hauler) {
            hauler.recallTo(this);
            return true;
        }
        recallRequested = true;
        setChanged();
        return true;
    }

    /** The entity has dumped and is about to cease to exist: take its charge back into the item. */
    public void onRecalled(ScrapHaulerEntity hauler) {
        ItemStack stack = items.get(HAULER_SLOT);
        if (stack.getItem() instanceof ScrapHaulerItem) {
            ScrapHaulerItem.setCharge(stack, hauler.charge());
        }
        deployed = false;
        haulerUuid = null;
        recallRequested = false;
        setChanged();
    }

    /** The entity is gone by some route that was not a recall. Unlock the slot; the item is still here. */
    public void onHaulerGone(UUID uuid) {
        if (deployed && uuid.equals(haulerUuid)) {
            deployed = false;
            haulerUuid = null;
            recallRequested = false;
            setChanged();
        }
    }

    /** Cargo arriving from the Hauler; what did not fit comes back. */
    public ItemStack receive(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = CARGO_START; i < SLOT_COUNT && !rest.isEmpty(); i++) {
            ItemStack slot = items.get(i);
            if (slot.isEmpty()) {
                items.set(i, rest);
                rest = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(slot, rest)) {
                int fit = Math.min(slot.getMaxStackSize() - slot.getCount(), rest.getCount());
                slot.grow(fit);
                rest.shrink(fit);
            }
        }
        if (rest.getCount() != stack.getCount()) {
            setChanged();
        }
        return rest;
    }

    /**
     * Ruling 19: breaking the Depot recalls the Hauler first, then drops everything. Order matters -
     * the recall dumps into the hold, and the hold is what drops.
     *
     * <p><b>The super call has to come AFTER the recall, and it did not.</b>
     * {@code BlockEntity.preRemoveSideEffects} already runs {@code Containers.dropContents} for any
     * {@code Container}, and {@code dropItemStack} EMPTIES the source stack as it drops it. So calling
     * super first threw the Hauler item on the floor carrying whatever charge it had at DEPLOY time,
     * and then {@code onRecalled} wrote the real field charge into a stack that was already gone and
     * already count-zero. Break a Depot whose Hauler has spent a full battery and you get the full
     * battery back; break one whose Hauler charged all day in the sun and you lose the day. Neither
     * announces itself, because the item that lands looks exactly right.
     *
     * <p>The existing test cannot see it: it deploys and breaks in the same tick, so the deploy-time
     * charge and the field charge are the same number.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
        if (level instanceof ServerLevel server && deployed) {
            recall(server);
        }
        // Now the hold holds everything, including whatever the recall just brought home, and the
        // Hauler's stack carries the charge it actually came back with.
        super.preRemoveSideEffects(pos, oldState);
        if (level instanceof ServerLevel) {
            items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        }
    }

    // ---- the container ---------------------------------------------------------------------

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
        ItemStack out = ContainerHelper.removeItem(items, slot, amount);
        if (!out.isEmpty()) {
            setChanged();
        }
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /**
     * What may go where: a Hauler in slot 0 only while none is deployed, sortable blocks in the hold.
     * On the container, so the hopper path and the player path read one rule.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == HAULER_SLOT) {
            return !deployed && stack.getItem() instanceof ScrapHaulerItem;
        }
        return SortableBlock.sortRolls(stack.getItem()) > 0;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return CARGO_FACES;
    }

    /** The hold takes garbage from any face; nothing goes into the Hauler slot by pipe. */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot != HAULER_SLOT && canPlaceItem(slot, stack);
    }

    /** A hopper drains the hold and never the Hauler, deployed or not: the third conservation row. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot != HAULER_SLOT;
    }

    // ---- the menu ------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.recompile.hauler_depot");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new HaulerDepotMenu(containerId, inventory, this, data);
    }

    // ---- persistence --------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        battery.serialize(output.child("battery"));
        output.putBoolean("deployed", deployed);
        output.putBoolean("recall", recallRequested);
        output.putInt("radius", chunkRadius);
        if (haulerUuid != null) {
            output.putString("hauler", haulerUuid.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        input.child("battery").ifPresent(battery::deserialize);
        savedEnergy = battery.getAmountAsInt();
        deployed = input.getBooleanOr("deployed", false);
        recallRequested = input.getBooleanOr("recall", false);
        chunkRadius = input.getIntOr("radius", DEFAULT_CHUNK_RADIUS);
        String uuid = input.getStringOr("hauler", "");
        haulerUuid = uuid.isEmpty() ? null : UUID.fromString(uuid);
    }
}
