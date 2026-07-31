package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * <p><b>No screen and no inventory</b>, per the mod's standing rule that machine GUIs are the exception.
 * You feed it by right-clicking with fuel, exactly as the Cutting Torch takes rags, and it converts burn
 * time into FE. Automation feeds it through the item capability rather than through a slot the player has
 * to look at ({@code docs/automation_policy_spec.md}).
 *
 * <p><b>Fuel is the vanilla fuel data map</b> ({@code data/neoforge/data_maps/item/furnace_fuels.json}),
 * read live via {@code level.fuelValues()}. So anything the Burn Barrel or a vanilla furnace will burn,
 * this will burn, and a pack retunes both at once. Deliberately not its own allowlist: the Burn Barrel's
 * allowlist exists to gate *smelting outputs*, which is a different job from "what counts as fuel".
 *
 * <p>Burn time converts at {@link #FE_PER_TICK} while lit, so a fuel's value here is exactly proportional
 * to its furnace value - an Oily Rag is worth the same relative to coal in both machines.
 */
public class BurnerGeneratorBlockEntity extends BlockEntity {

    /** FE per tick while burning. First-pass; balance is #36. */
    public static final int FE_PER_TICK = 20;
    /** Buffer. Larger than the panel's: this one runs in bursts and must not waste a rag's tail. */
    public static final int CAPACITY = 20_000;
    private static final int TRANSFER_PER_TICK = 256;

    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(CAPACITY, CAPACITY, CAPACITY);

    /** Ticks of burn left. The only other state; no fuel slot, because there is no slot. */
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

    /**
     * Accept one item's worth of fuel. Returns false when the item is not fuel, or when the generator is
     * still burning enough that the new fuel would be mostly wasted.
     *
     * <p>The second condition mirrors the Cutting Torch's refusal to overfill: silently swallowing a rag
     * for a fraction of its value is the kind of loss a player cannot see happening.
     */
    public boolean addFuel(Level level, net.minecraft.world.item.ItemStack stack) {
        int duration = level.fuelValues().burnDuration(stack);
        if (duration <= 0 || burnTime > 0) {
            return false;
        }
        burnTime = duration;
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            BurnerGeneratorBlockEntity generator) {
        boolean wasLit = generator.isLit();
        if (generator.burnTime > 0) {
            generator.burnTime--;
            try (Transaction transaction = Transaction.openRoot()) {
                generator.battery.insert(FE_PER_TICK, transaction);
                transaction.commit();
            }
        }
        generator.pushToNeighbours(level, pos);
        if (wasLit != generator.isLit()) {
            // The blockstate is the only outward sign it is running, so it has to follow the burn or a
            // player reads a dead generator as a lit one.
            level.setBlock(pos, state.setValue(
                com.flatts.recompile.content.block.BurnerGeneratorBlock.LIT, generator.isLit()),
                net.minecraft.world.level.block.Block.UPDATE_ALL);
            generator.setChanged();
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

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        battery.serialize(output.child("energy"));
        output.putInt("burn_time", burnTime);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("energy").ifPresent(battery::deserialize);
        burnTime = input.getIntOr("burn_time", 0);
    }
}
