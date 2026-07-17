package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
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
 * The Rain Collector's tank (design P1.10): a one-slot water tank that fills from rain.
 *
 * <p>It is a real {@link ResourceHandler}&lt;{@link FluidResource}&gt; on 26.1's transfer
 * API (the successor to the old {@code IFluidHandler} capability, and what Mekanism and the
 * rest use now), so pipes and pumps move water through it as they would any tank - it is the
 * block that plugs into the pump end of the design's collector -&gt; cauldron -&gt; pumps
 * ladder. The one restriction is water-only ({@link #isValid} on the tank), so it can never
 * become a general fluid store.
 *
 * <p>A BlockEntity because it must <em>hold</em> a fluid - the same "storage is the honest
 * exception" line the Scrap Barrel sits on. No renderer: the level is not drawn for v1.
 */
public class RainCollectorBlockEntity extends BlockEntity {

    /** Four buckets. */
    public static final int CAPACITY = 4000;
    /** Water added per successful rain tick (the fill is slow - see the block's chance gate). */
    private static final int RAIN_MB = 100;
    /** A glass bottle's worth. */
    private static final int BOTTLE_MB = 250;

    private static final FluidResource WATER = FluidResource.of(Fluids.WATER);

    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(1, CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return resource.value() == Fluids.WATER;
        }
    };

    public RainCollectorBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(RCBlockEntities.RAIN_COLLECTOR.get(), worldPosition, blockState);
    }

    /** The capability handed to pipes, pumps, and {@code FluidUtil} bucket interactions. */
    public ResourceHandler<FluidResource> fluidHandler() {
        return tank;
    }

    /** A successful rain tick adds water, capped at capacity. */
    public void catchRain() {
        // The transfer API only commits changes when the transaction commits; a root
        // transaction closed without committing rolls back. So: open, insert, commit.
        try (Transaction transaction = Transaction.openRoot()) {
            if (tank.insert(WATER, RAIN_MB, transaction) > 0) {
                transaction.commit();
                setChanged();
            }
        }
    }

    public boolean canFillBottle() {
        return tank.getResource(0).value() == Fluids.WATER && tank.getAmountAsInt(0) >= BOTTLE_MB;
    }

    public void drainBottle() {
        try (Transaction transaction = Transaction.openRoot()) {
            if (tank.extract(WATER, BOTTLE_MB, transaction) > 0) {
                transaction.commit();
                setChanged();
            }
        }
    }

    /** For GameTests: current stored mB. */
    public int storedWater() {
        return tank.getAmountAsInt(0);
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
}
