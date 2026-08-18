package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The Water Tank's contents, and the one component in this mod that is not inert (#229).
 *
 * <p><b>Owner ruling, 2026-08-18, after playtest: a water tank should hold water and work like a
 * tank.</b> That reverses P2.4-R item 6 for this block and this block only - the Pump still moves no
 * fluid, the Solar Panel still detects no light, the Motor still turns nothing. The argument for the
 * carve-out is that the Water Tank is the only component whose name states a <b>capacity</b> rather
 * than an action. A pump that does not pump reads as a part; a tank that does not hold reads as
 * broken, and two playtesters reported it as broken ninety minutes apart - one of them in the same
 * sentence as a genuine bug, which is the cost that decided it.
 *
 * <p><b>Water only</b>, like the Rain Collector's tank and the Tree Nursery's. The block is called a
 * Water Tank and the ruling was that it should hold water; a general fluid store is a different block
 * with a different name, and leachate in a thing labelled "water" is the same category of surprise
 * this issue exists to remove.
 *
 * <p><b>Half a Rain Collector's capacity</b>, deliberately. The collector is built <em>from</em> a
 * tank plus a Copper Pipe, so the machine ought to be more than the part - and the two are for
 * different things: a collector accumulates rain over hours and wants headroom, a tote carries water
 * from A to B.
 */
public class WaterTankBlockEntity extends BlockEntity {

    /** Two buckets, against the Rain Collector's four. */
    public static final int CAPACITY = 2000;

    private static final FluidResource WATER = FluidResource.of(Fluids.WATER);

    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.value() == Fluids.WATER;
        }
    };

    public WaterTankBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.WATER_TANK.get(), worldPosition, blockState);
    }

    /** The capability handed to pipes, pumps and {@code FluidUtil} bucket interactions. */
    public ResourceHandler<FluidResource> fluidHandler() {
        return tank;
    }

    /** For GameTests and Jade: current stored mB. */
    public int storedWater() {
        return tank.getAmountAsInt(0);
    }

    /** For GameTests: put water in without going through a bucket. */
    public void fill(int amount) {
        // The transfer API only commits when the transaction commits; a root transaction closed
        // without committing rolls back. So: open, insert, commit.
        try (Transaction transaction = Transaction.openRoot()) {
            if (tank.insert(WATER, amount, transaction) > 0) {
                transaction.commit();
                setChanged();
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("tank").ifPresent(tank::deserialize);
    }

    // ---------------- carry water through break + replace (item data component) ----------------
    // saveAdditional survives SAVE/LOAD and nothing else: breaking the block destroys the
    // BlockEntity, so without the component below a tank emptied itself every time it was picked up.
    // The Rain Collector already documents this trap; a tank that loses its contents on being moved
    // is a tank in name only, which is the whole complaint this block is answering.

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        int stored = tank.getAmountAsInt(0);
        if (stored > 0) {
            builder.set(RCDataComponents.TANK_WATER.get(), stored);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter getter) {
        super.applyImplicitComponents(getter);
        Integer stored = getter.get(RCDataComponents.TANK_WATER.get());
        if (stored != null && stored > 0) {
            fill(stored);
        }
    }
}
