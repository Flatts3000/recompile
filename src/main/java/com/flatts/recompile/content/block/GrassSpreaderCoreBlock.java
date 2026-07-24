package com.flatts.recompile.content.block;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.content.block.multiblock.MultiblockCoreBlock;
import com.flatts.recompile.registry.RCBlocks;
import com.flatts.recompile.registry.RCTags;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Grass Spreader (design P2.4-R3): rung 1 of the reclamation chain, and a <b>drip irrigator</b> - a
 * tower ringed by four copper spigots that patiently water the surrounding ground and turns dead earth to
 * grass, forever, <b>consuming nothing</b>.
 *
 * <p>It consumes nothing because the machine carries its own supply: an actual Rain Collector is
 * built into the structure. The cost is one steep build, and the ongoing pressure comes from P1.7-R
 * instead - the junkyard takes healed ground back, so a spreader has to out-pace erosion. That makes
 * <b>its radius exactly the land you can hold at rung 1</b>; beyond it, the frontier wins.
 *
 * <p><b>Nearest-first is the one rule that matters</b>, and it buys two behaviours at once: green
 * reads as growing outward from the machine, and ground the frontier just took back is <em>closer</em>
 * than untouched ground, so it is repaired before new ground is broken. No repair pass, no stored
 * progress, nothing serialised.
 *
 * <p><b>No BlockEntity.</b> Radius and rate are config and the search recomputes from the world, so
 * the machine stores nothing; it runs on a self-rescheduling block tick, which survives save/load on
 * its own. That keeps the framework's "no BlockEntity for the structure" line intact.
 */
public class GrassSpreaderCoreBlock extends MultiblockCoreBlock {

    public static final MapCodec<GrassSpreaderCoreBlock> CODEC = simpleCodec(GrassSpreaderCoreBlock::new);

    /** What one attempt did. Returned so GameTests can assert the reason, not just the result. */
    public enum Outcome {
        /** Converted a block. */
        SPREAD,
        /** Nothing eligible in range - the radius is fully healed (for now). */
        IDLE,
        /** Not assembled, so not running. */
        UNFORMED,
        /** Disabled by config. */
        DISABLED
    }

    /**
     * Offsets within the largest configurable radius, sorted nearest-first and built once. Walking
     * this and stopping at the first hit is what makes the common case cheap: a machine with work
     * right next to it exits almost immediately, and only a fully-healed radius pays for the whole
     * walk - which is exactly the case that then drops to the idle interval.
     */
    private static final List<Vec3i> OFFSETS = buildOffsets(64);

    public GrassSpreaderCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GrassSpreaderCoreBlock> codec() {
        return CODEC;
    }

    @Override
    protected Multiblock createBlueprint() {
        List<Multiblock.Cell> cells = new ArrayList<>();
        // An inert Water Tank, NOT the Rain Collector itself: a machine may never take another
        // machine's core as a component (see Multiblock's constructor). The collector is consumed
        // in the tank's recipe instead, so the progression survives without a second live core
        // inside this structure.
        cells.add(new Multiblock.Cell(new Vec3i(0, 1, 0),
            RCBlocks.WATER_TANK.get(), RCBlocks.WATER_TANK.get()));
        cells.add(new Multiblock.Cell(new Vec3i(0, 2, 0),
            RCBlocks.PUMP.get(), RCBlocks.GRASS_SPREADER_FRAME.get()));
        // The drip ring: four copper pipes around the manifold, each becoming a spigot. This is the
        // first blueprint that is not a plain column - the framework already allowed it, since a
        // Cell is just an offset.
        for (Vec3i side : List.of(new Vec3i(1, 2, 0), new Vec3i(-1, 2, 0),
                                  new Vec3i(0, 2, 1), new Vec3i(0, 2, -1))) {
            cells.add(new Multiblock.Cell(side,
                RCBlocks.COPPER_PIPE.get(), RCBlocks.GRASS_SPREADER_SPIGOT.get()));
        }
        // The solar panel keeps its own appearance, so it is both the component and the formed
        // block - a cell that does not change costs one block, not two.
        cells.add(new Multiblock.Cell(new Vec3i(0, 3, 0),
            RCBlocks.SOLAR_PANEL.get(), RCBlocks.SOLAR_PANEL.get()));
        return new Multiblock(List.copyOf(cells));
    }

    private static List<Vec3i> buildOffsets(int max) {
        List<Vec3i> offsets = new ArrayList<>();
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                if (dx * dx + dz * dz <= max * max) {
                    offsets.add(new Vec3i(dx, 0, dz));
                }
            }
        }
        offsets.sort(Comparator.comparingInt(o -> o.getX() * o.getX() + o.getZ() * o.getZ()));
        return List.copyOf(offsets);
    }

    // ---------------- the work ----------------

    /**
     * Self-rescheduling: every pulse books the next one, so the machine keeps itself alive without
     * storing anything. An unformed core books nothing and simply stops - {@link #onFormed} starts
     * it again - so a half-built tower costs nothing at all.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Outcome outcome = spreadOnce(level, pos);
        if (outcome == Outcome.UNFORMED) {
            return;
        }
        schedule(level, pos, outcome == Outcome.IDLE
            ? RCConfig.GRASS_SPREADER_IDLE_INTERVAL_TICKS.get()
            : RCConfig.GRASS_SPREADER_INTERVAL_TICKS.get());
    }

    /**
     * Book the next pulse, but never a second one.
     *
     * <p>The dedupe matters more than it looks: the machine keeps itself alive by rescheduling from
     * its own tick, so if a stray second tick were ever booked the two chains would both reschedule
     * and the machine would run at double rate - and the next stray would double it again. Cheap
     * guard against a compounding bug that would be very hard to read from the symptoms.
     */
    private static void schedule(Level level, BlockPos pos, int delay) {
        if (level.isClientSide()) {
            return;
        }
        Block block = level.getBlockState(pos).getBlock();
        if (!level.getBlockTicks().hasScheduledTick(pos, block)) {
            level.scheduleTick(pos, block, delay);
        }
    }

    /**
     * Convert the single nearest eligible block. The static entry point GameTests drive directly
     * rather than waiting on scheduled ticks - the convention {@code SortableBlock.sortOnce} and
     * {@code RCEncroachment.encroachOnce} set.
     */
    public static Outcome spreadOnce(ServerLevel level, BlockPos corePos) {
        if (!RCConfig.GRASS_SPREADER_ENABLED.get()) {
            return Outcome.DISABLED;
        }
        if (!isFormed(level.getBlockState(corePos))) {
            return Outcome.UNFORMED;
        }
        int radius = RCConfig.GRASS_SPREADER_RADIUS.get();
        int tolerance = RCConfig.GRASS_SPREADER_VERTICAL_TOLERANCE.get();
        int radiusSq = radius * radius;

        for (Vec3i offset : OFFSETS) {
            if (offset.getX() * offset.getX() + offset.getZ() * offset.getZ() > radiusSq) {
                break;   // sorted nearest-first, so the first miss ends the walk
            }
            BlockPos column = corePos.offset(offset);
            if (!level.hasChunkAt(column)) {
                continue;   // never pull a chunk in just to water it
            }
            // Scan the tolerance band top-down for the highest eligible ground.
            //
            // Deliberately NOT the world heightmap: that reports the topmost block of the whole
            // column, so anything under a roof, an overhang, or a walkway is invisible to it, and
            // a machine indoors would see only the ceiling. A sprinkler waters ground near its own
            // level, which is exactly what this band expresses - and it makes the vertical
            // tolerance the single thing deciding reach, instead of splitting that decision
            // between a heightmap lookup and a range check.
            for (int dy = tolerance; dy >= -tolerance; dy--) {
                BlockPos candidate = column.above(dy);
                if (isSpreadable(level, candidate)) {
                    level.setBlockAndUpdate(candidate, Blocks.GRASS_BLOCK.defaultBlockState());
                    return Outcome.SPREAD;
                }
            }
        }
        return Outcome.IDLE;
    }

    /**
     * Whether this block should become grass.
     *
     * <p>The last check is the one that is easy to leave out and expensive to omit: if grass could
     * not survive here (roofed, too dark), converting it means vanilla kills it straight back to
     * dirt and the machine converts the same block forever, burning its whole budget on one cell.
     */
    public static boolean isSpreadable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // SPREAD_IMMUNE, not ENCROACHMENT_IMMUNE: the latter contains coarse dirt (encroachment's
        // revert target), which is this machine's primary target. The two systems mean opposite
        // things by "immune", so they get separate tags.
        if (!state.is(RCTags.SPREADABLE) || state.is(RCTags.SPREAD_IMMUNE)) {
            return false;
        }
        BlockState above = level.getBlockState(pos.above());
        return !above.isSolidRender() && level.getRawBrightness(pos.above(), 0) >= 4;
    }

    @Override
    protected void onFormed(Level level, BlockPos pos) {
        schedule(level, pos, RCConfig.GRASS_SPREADER_INTERVAL_TICKS.get());
    }

    // No onDisbanded needed: the next scheduled tick finds an unformed core and stops rescheduling.
}
