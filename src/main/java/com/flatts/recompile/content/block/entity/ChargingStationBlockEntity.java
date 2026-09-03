package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.item.GarbageVacuumItem;
import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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

/**
 * The Charging Station's state (#336): one docked vacuum and a buffer the generators fill.
 *
 * <p>Deliberately <b>not</b> a {@link net.minecraft.world.Container} and with no item capability, the
 * Display Pedestal's shape: it holds one thing, setting it down and picking it up IS the interaction,
 * and nothing may reach in. Only the buffer is exposed, insert-only, so a Solar Panel or Burner against
 * any face charges it with no wire.
 *
 * <p><b>The item is charged through its capability, not by poking the component.</b>
 * {@code Capabilities.Energy.ITEM} on the docked stack is the same door any other mod's charger would
 * use, so this block proves that door works every time it runs; the vacuum drains itself through the
 * component directly, and both read one number. {@link ItemAccess#forStack} mutates the docked stack
 * in place, which is exactly what a slot wants.
 */
public class ChargingStationBlockEntity extends BlockEntity {

    /** A Burner's worth, so a dock can bank a night's charge while the vacuum is out. */
    public static final int CAPACITY = 20_000;

    /** Ten Burners' output: the dock is the bottleneck a player waits at, so it should not be slow. */
    public static final int TRANSFER_PER_TICK = 200;

    private NonNullList<ItemStack> docked = NonNullList.withSize(1, ItemStack.EMPTY);
    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(CAPACITY, CAPACITY, CAPACITY);

    /** What the last tick moved into the vacuum, for Jade's rate line. */
    private int lastTransfer;

    /** The buffer level as of the last {@code setChanged}; see {@link #markBufferDirty}. */
    private int savedEnergy;

    public ChargingStationBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.CHARGING_STATION.get(), pos, state);
    }

    public SimpleEnergyHandler battery() {
        return battery;
    }

    public int stored() {
        return battery.getAmountAsInt();
    }

    public int lastTransfer() {
        return lastTransfer;
    }

    /** The docked vacuum (may be empty). */
    public ItemStack docked() {
        return docked.get(0);
    }

    public boolean isEmpty() {
        return docked.get(0).isEmpty();
    }

    /** Set a vacuum on the dock, handing back whatever was there. */
    public ItemStack dock(ItemStack vacuum) {
        ItemStack previous = docked.get(0);
        docked.set(0, vacuum);
        setChanged();
        return previous;
    }

    /** Take the docked vacuum off. */
    public ItemStack undock() {
        ItemStack out = docked.get(0);
        docked.set(0, ItemStack.EMPTY);
        lastTransfer = 0;
        setChanged();
        return out;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChargingStationBlockEntity dock) {
        dock.lastTransfer = dock.chargeOnce();
        dock.markBufferDirty();
    }

    /**
     * Mark the block entity dirty when the BUFFER moved, whatever the dock is holding.
     *
     * <p>{@link #chargeOnce} only reaches {@code setChanged} when it actually moved energy into a
     * docked vacuum, and the generator's insert path is a {@code LimitingEnergyHandler} wrapped around
     * the raw battery - it has no back-reference to this block entity, so an insert marks nothing.
     * The result was that the case this block advertises by name in its javadoc, a dock banking a
     * night's charge while the vacuum is out, was the one path that never saved: a Solar Panel fills an
     * empty dock to 20,000 FE, nothing else dirties the chunk, the region unloads, and the buffer
     * reloads at whatever it last happened to write.
     *
     * <p>Compared against a remembered value rather than called unconditionally, because
     * {@code setChanged} every tick on every dock in the world is a real cost for a block that is
     * usually idle.
     */
    private void markBufferDirty() {
        int now = battery.getAmountAsInt();
        if (now != savedEnergy) {
            savedEnergy = now;
            setChanged();
        }
    }

    /** One tick of charging at {@code pos}. The static entry point the GameTests drive, like {@code burnOnce}. */
    public static int chargeOnce(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ChargingStationBlockEntity dock ? dock.chargeOnce() : 0;
    }

    private int chargeOnce() {
        ItemStack stack = docked.get(0);
        if (stack.isEmpty() || battery.getAmountAsInt() <= 0
                || GarbageVacuumItem.charge(stack) >= GarbageVacuumItem.capacityOf(stack)) {
            return 0;
        }
        EnergyHandler target = ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
        if (target == null) {
            return 0;
        }
        int moved;
        try (Transaction transaction = Transaction.openRoot()) {
            moved = EnergyHandlerUtil.move(battery, target, TRANSFER_PER_TICK, transaction);
            transaction.commit();
        }
        if (moved > 0) {
            setChanged();
        }
        return moved;
    }

    /** Drop the vacuum on any removal (player break, explosion, piston, /setblock, mod replace). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
        super.preRemoveSideEffects(pos, oldState);
        if (level != null && !level.isClientSide() && !isEmpty()) {
            Block.popResource(level, pos, docked.get(0));
            docked.set(0, ItemStack.EMPTY);
        }
    }

    // ---- persistence ------------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, docked);
        battery.serialize(output.child("battery"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        docked = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, docked);
        input.child("battery").ifPresent(battery::deserialize);
        savedEnergy = battery.getAmountAsInt();
    }
}
