package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.registry.RCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
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
 * The Solar Panel, which as of #72 actually generates (spec {@code docs/hydroponics_spec.md}).
 *
 * <p><b>This block was inert for the mod's whole life</b> - "it does not detect light, emit redstone, or
 * generate power" - because P3.5 locked "no RF before the Nether". That lock is reversed
 * ({@code trashlands/docs/design_decisions.md} P3.5); the panel is the half of the power tier that needed
 * no new art, since it was already named and shaped like the thing it now is.
 *
 * <p><b>Panels inside the Grass Spreader and Tree Nursery generate too, and those machines do not
 * consume.</b> That is deliberate and it is the only version that does not break saves: making shipped
 * machines start requiring power would strand every existing base. A panel embedded in a spreader quietly
 * feeding a neighbour reads as a reward for compact building rather than as a bug.
 *
 * <p><b>The Pump stays inert.</b> That is P2.3 ("Recompile converts, Create moves"), a separate decision
 * this does not touch - so do not read this class as permission to animate the rest of the components.
 */
public class SolarPanelBlockEntity extends BlockEntity {

    /** FE per tick in direct daylight. First-pass; balance is #36. */
    public static final int GENERATION_PER_TICK = 2;
    /** Small buffer - a panel is a trickle, not a battery. First-pass; balance is #36. */
    public static final int CAPACITY = 4_000;
    /** How much it will hand a neighbour per tick. Generous: the buffer exists to smooth night, not to hoard. */
    private static final int TRANSFER_PER_TICK = 64;

    /**
     * The buffer accepts insert as well as extract, which is not what a solar panel sounds like.
     *
     * <p>Generation goes through {@code insert}, so a zero insert limit would mean reaching past the
     * handler into its protected state and losing transaction safety. Allowing it also makes a panel a
     * tiny battery a pipe can charge - harmless, occasionally useful, and cheaper than wrapping the
     * capability in a limiter purely to forbid something nobody wanted to do.
     */
    private final SimpleEnergyHandler battery = new SimpleEnergyHandler(CAPACITY, CAPACITY, CAPACITY);

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(RCBlockEntities.SOLAR_PANEL.get(), pos, state);
    }

    public EnergyHandler energyHandler() {
        return battery;
    }

    /** Exposed for tests and for Jade; the handler's own accessor is the same number. */
    public int stored() {
        return battery.getAmountAsInt();
    }

    /**
     * Output scaled by how much daylight actually reaches the panel.
     *
     * <p>Sky brightness minus {@code getSkyDarken} is vanilla's own daylight-detector maths, and using it
     * rather than an {@code isDay} branch gets night, dawn, dusk and weather from one expression - and
     * keeps the panel agreeing with the block it is a reskin of.
     *
     * <p>It also means weather dims rather than kills. A panel that died the moment it rained would put
     * the power tier at war with the Rain Collector, which is only useful in exactly that weather.
     */
    public static int outputAt(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        if (!level.canSeeSky(above)) {
            return 0;
        }
        int daylight = level.getBrightness(LightLayer.SKY, above) - level.getSkyDarken();
        if (daylight <= 0) {
            return 0;
        }
        return Math.max(1, GENERATION_PER_TICK * daylight / 15);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarPanelBlockEntity panel) {
        int made = outputAt(level, pos);
        if (made > 0) {
            try (Transaction transaction = Transaction.openRoot()) {
                panel.battery.insert(made, transaction);
                transaction.commit();
            }
        }
        panel.pushToNeighbours(level, pos);
    }

    /**
     * Hand energy to any adjacent block that will take it.
     *
     * <p>Pushing rather than waiting to be pulled so the mod works with no pipe mod installed at all -
     * put a panel against a machine and it runs. A pipe mod is then an upgrade for reaching further, not
     * a requirement for having power.
     */
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

    /** Test entry point: one tick of generation, the {@code sortOnce} convention. */
    public static int generateOnce(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SolarPanelBlockEntity panel) {
            serverTick(level, pos, level.getBlockState(pos), panel);
            return panel.stored();
        }
        return -1;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        battery.serialize(output.child("energy"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("energy").ifPresent(battery::deserialize);
    }
}
