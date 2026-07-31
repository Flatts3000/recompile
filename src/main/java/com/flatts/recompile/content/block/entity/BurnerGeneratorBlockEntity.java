package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.block.BurnerGeneratorBlock;
import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import com.flatts.recompile.content.menu.BurnerGeneratorMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Burner Generator (#72): burns refuse for power, and the half of the power tier that works at night.
 *
 * <p><b>It has a fuel buffer and a screen</b> (owner call, 2026-07-31): if the Burn Barrel gets a UI for
 * holding fuel, so does this. The screen is bespoke ({@link BurnerGeneratorMenu}) because it carries a
 * <b>power meter</b>, and no vanilla screen has one - a generator whose stored energy is invisible is a
 * machine you cannot reason about. It borrowed vanilla's hopper screen first; that showed the fuel and
 * not the power, which was the half that mattered.
 *
 * <p>The buffer is the real gain over the right-click-to-feed version this replaces: the generator runs
 * unattended, and <b>automation can fuel it</b>, which one-item-at-a-time by hand could never support.
 *
 * <p><b>Fuel is the vanilla fuel data map</b> ({@code data/neoforge/data_maps/item/furnace_fuels.json}),
 * read live via {@code level.fuelValues()}. So anything the Burn Barrel or a vanilla furnace will burn,
 * this will burn, and a pack retunes both at once. Deliberately not its own allowlist: the Burn Barrel's
 * allowlist exists to gate <em>smelting outputs</em>, a different job from "what counts as fuel".
 *
 * <p>Burn time converts at {@link #FE_PER_TICK} while lit, so a fuel's worth here stays proportional to
 * its furnace worth - an Oily Rag is the same fraction of a coal in both machines.
 */
public class BurnerGeneratorBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {

    /** FE per tick while burning. First-pass; balance is #36. */
    public static final int FE_PER_TICK = 20;
    /** Buffer. Larger than the panel's: this one runs in bursts and must not waste a rag's tail. */
    public static final int CAPACITY = 20_000;
    /** Fuel slots - one row on the screen. */
    public static final int FUEL_SLOTS = 5;
    private static final int TRANSFER_PER_TICK = 256;

    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(CAPACITY, CAPACITY, CAPACITY);
    private NonNullList<ItemStack> items = NonNullList.withSize(FUEL_SLOTS, ItemStack.EMPTY);

    /** Ticks of burn left on the item currently alight. */
    private int burnTime;

    public BurnerGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.BURNER_GENERATOR.get(), pos, state);
    }

    public EnergyHandler energyHandler() {
        return battery;
    }

    public int stored() {
        return battery.getAmountAsInt();
    }

    public int burnTime() {
        return burnTime;
    }

    public boolean isLit() {
        return burnTime > 0;
    }

    // ---------------- the burn ----------------

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            BurnerGeneratorBlockEntity generator) {
        boolean wasLit = generator.isLit();
        if (generator.burnTime > 0) {
            generator.burnTime--;
            try (Transaction transaction = Transaction.openRoot()) {
                generator.battery.insert(FE_PER_TICK, transaction);
                transaction.commit();
            }
        } else {
            generator.lightNextFuel(level);
        }
        generator.pushToNeighbours(level, pos);
        if (wasLit != generator.isLit()) {
            // The blockstate is the only outward sign it is running, so it has to follow the burn or a
            // player reads a dead generator as a lit one.
            level.setBlock(pos, state.setValue(BurnerGeneratorBlock.LIT, generator.isLit()),
                Block.UPDATE_ALL);
            generator.setChanged();
        }
    }

    /** Consume one item from the buffer and light it. Nothing burns while the battery is already full. */
    private void lightNextFuel(Level level) {
        if (battery.getAmountAsInt() >= CAPACITY) {
            return;   // do not spend fuel making energy with nowhere to go
        }
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            int duration = level.fuelValues().burnDuration(stack);
            if (duration > 0) {
                burnTime = duration;
                stack.shrink(1);
                setChanged();
                return;
            }
        }
    }

    /** Same push-first design as the Solar Panel: a generator against a machine works with no pipes. */
    private void pushToNeighbours(Level level, BlockPos pos) {
        if (battery.getAmountAsInt() <= 0) {
            return;
        }
        for (Direction side : Direction.values()) {
            EnergyHandler neighbour = level.getCapability(
                Capabilities.Energy.BLOCK, pos.relative(side), side.getOpposite());
            if (neighbour == null) {
                continue;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                EnergyHandlerUtil.move(battery, neighbour, TRANSFER_PER_TICK, transaction);
                transaction.commit();
            }
            if (battery.getAmountAsInt() <= 0) {
                return;
            }
        }
    }

    /** Test entry point: one tick, the {@code sortOnce} convention. */
    public static int burnOnce(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BurnerGeneratorBlockEntity generator) {
            serverTick(level, pos, level.getBlockState(pos), generator);
            return generator.stored();
        }
        return -1;
    }

    // ---------------- container ----------------

    @Override
    public int getContainerSize() {
        return FUEL_SLOTS;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> replacement) {
        this.items = replacement;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.recompile.burner_generator");
    }

    /** Feeds the screen's power meter. Capacity is a constant, so only the two live values cross. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? battery.getAmountAsInt() : burnTime;
        }

        @Override
        public void set(int index, int value) {
            // Server-authoritative: the client mirrors these, it never writes them.
        }

        @Override
        public int getCount() {
            return BurnerGeneratorMenu.DATA_SIZE;
        }
    };

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new BurnerGeneratorMenu(containerId, inventory, this, data);
    }

    /** Only fuel, so neither a player nor a pipe can park something unburnable in the buffer. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return this.level != null && this.level.fuelValues().burnDuration(stack) > 0;
    }

    // Every face accepts fuel and gives nothing back - there is no output to take, and leaving extraction
    // open would let a pipe pull the fuel straight back out of a generator it just filled.

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[] {0, 1, 2, 3, 4};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        battery.serialize(output.child("energy"));
        output.putInt("burn_time", burnTime);
        ContainerHelper.saveAllItems(output, this.items);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("energy").ifPresent(battery::deserialize);
        burnTime = input.getIntOr("burn_time", 0);
        this.items = NonNullList.withSize(FUEL_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
    }
}
