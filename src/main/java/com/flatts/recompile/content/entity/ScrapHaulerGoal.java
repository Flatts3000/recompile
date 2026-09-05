package com.flatts.recompile.content.entity;

import com.flatts.recompile.content.block.entity.HaulerDepotBlockEntity;
import com.flatts.recompile.content.entity.ScrapHaulerEntity.Mode;
import com.flatts.recompile.registry.RCFluids;
import com.flatts.recompile.registry.RCTags;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Hauler's one behaviour: the fetch-and-return loop from the spec, as a state machine.
 *
 * <p>Seek a takeable block inside the Depot's work area, take it, repeat until the hold is full or
 * there is nothing left, go home, dump, and either go again or park. Two things interrupt that and are
 * handled here rather than as separate goals, because they are states of the same loop: running flat
 * (park where it stands and wait for the sun) and a Depot with no room (wait beside it).
 *
 * <p><b>It prefers a pile it can reach over the nearest one</b>, decided by trying rather than by
 * asking. A target the navigation refuses three times running, or that stops getting closer for five
 * seconds, is dropped and blacklisted for a while. It used to ask for a path before committing, and
 * that read a transient refusal - the tick after spawning, before the mob is on the ground - as
 * "unreachable" and blacklisted the pile for twenty seconds. On terrain made of {@code FallingBlock}s
 * that move, "nearest" is often "unreachable", and a machine that stands facing a pile it cannot get
 * to is the pathing failure every quarry ships with; the answer is patience, not prediction.
 *
 * <p><b>Fire and leachate are refused as targets as well as as paths.</b> The navigation maluses stop
 * it walking through them; this stops it choosing a block whose neighbour is one, which would leave it
 * standing in the hazard once it arrived.
 */
public class ScrapHaulerGoal extends Goal {

    /**
     * How far above or below the Depot a pile may be and still count. The area is chunks (owner,
     * 2026-09-05), which have no vertical extent worth searching, and a Depot on the surface has no
     * business sending its Hauler down a sewer or up a smokestack.
     */
    public static final int VERTICAL_REACH = 24;

    /**
     * How far down from the heightmap top a column is read. The top block is not always the pile:
     * a leaf, a snow layer, a slab somebody left, or the harness's own lid over a test plot all sit in
     * the motion-blocking heightmap above the thing underneath. Eight reads per column is still cheap.
     */
    public static final int COLUMN_DEPTH = 8;

    /** How close it has to be to act on a block or the Depot. Squared, in blocks. */
    private static final double REACH_SQ = 2.6 * 2.6;

    private static final int STUCK_TICKS = 100;
    private static final int BLACKLIST_TICKS = 400;
    private static final int RETRY_TICKS = 20;

    private static final int PATH_FAILURES_BEFORE_BLACKLIST = 3;

    /**
     * One block per this many ticks, the Garbage Vacuum's own cadence. Without it the machine took a
     * block every tick it stood inside a cluster - twenty a second, a full hold in three, and a trail
     * of flying blocks it had outrun - which is both the wrong look and a rate no balance pass could
     * reason about. The first-pass number is the vacuum's, so the two machines take at one speed.
     */
    private static final int INTAKE_PERIOD_TICKS = com.flatts.recompile.content.item.GarbageVacuumItem.INTAKE_PERIOD_TICKS;

    private final ScrapHaulerEntity hauler;
    private @Nullable BlockPos target;
    private int ticksOnTarget;
    private int pathFailures;
    private int intakeCooldown;
    private double lastDistanceSq;
    private int timer;
    private final java.util.Map<BlockPos, Integer> blacklist = new java.util.HashMap<>();

    public ScrapHaulerGoal(ScrapHaulerEntity hauler) {
        this.hauler = hauler;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return hauler.depotPos() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        timer = 0;
    }

    @Override
    public void stop() {
        hauler.getNavigation().stop();
        target = null;
    }

    @Override
    public void tick() {
        if (!(hauler.level() instanceof ServerLevel level)) {
            return;
        }
        blacklist.replaceAll((pos, ticks) -> ticks - 1);
        blacklist.values().removeIf(ticks -> ticks <= 0);

        switch (hauler.mode()) {
            case SEEKING -> seek(level);
            case RETURNING -> goHome(level);
            case DUMPING, WAITING_DEPOT -> dump(level);
            case PARKED_FLAT -> parkedFlat();
            case PARKED_IDLE -> parkedIdle(level);
        }
    }

    // ---- states --------------------------------------------------------------------------------

    private void seek(ServerLevel level) {
        if (hauler.flat()) {
            park(Mode.PARKED_FLAT);
            return;
        }
        if (hauler.cargoFull()) {
            hauler.setMode(Mode.RETURNING);
            target = null;
            return;
        }
        if (target == null || !takeable(level, target)) {
            target = findTarget(level);
            ticksOnTarget = 0;
            pathFailures = 0;
            lastDistanceSq = Double.MAX_VALUE;
            if (target == null) {
                // Out of work: go home if carrying anything, otherwise idle where it stands.
                hauler.setMode(hauler.cargoEmpty() ? Mode.PARKED_IDLE : Mode.RETURNING);
                hauler.getNavigation().stop();
                return;
            }
        }
        Vec3 centre = Vec3.atCenterOf(target);
        hauler.getLookControl().setLookAt(centre);
        double distSq = hauler.distanceToSqr(centre);
        if (distSq <= REACH_SQ) {
            hauler.getNavigation().stop();
            if (intakeCooldown > 0) {
                intakeCooldown--;
                return;
            }
            if (!hauler.take(level, target)) {
                blacklist.put(target, BLACKLIST_TICKS);
            }
            intakeCooldown = INTAKE_PERIOD_TICKS;
            target = null;
            return;
        }
        if (!progressing(distSq)) {
            blacklist.put(target, BLACKLIST_TICKS);
            target = null;
            return;
        }
        if (hauler.getNavigation().isDone() || ++timer % RETRY_TICKS == 0) {
            // Aim at the air above the pile, the way MoveToBlockGoal does: a path INTO a solid block
            // has no final node, and the reach check is against the block itself anyway.
            boolean planned = hauler.getNavigation().moveTo(centre.x, centre.y + 1.0, centre.z, 1.0);
            if (planned) {
                pathFailures = 0;
            } else if (++pathFailures >= PATH_FAILURES_BEFORE_BLACKLIST) {
                blacklist.put(target, BLACKLIST_TICKS);
                target = null;
            }
        }
    }

    private void goHome(ServerLevel level) {
        BlockPos home = hauler.depotPos();
        if (home == null) {
            return;
        }
        if (hauler.flat()) {
            park(Mode.PARKED_FLAT);
            return;
        }
        Vec3 centre = Vec3.atCenterOf(home);
        hauler.getLookControl().setLookAt(centre);
        if (hauler.distanceToSqr(centre) <= REACH_SQ + 1.0) {
            hauler.getNavigation().stop();
            hauler.setMode(Mode.DUMPING);
            return;
        }
        if (hauler.getNavigation().isDone() || ++timer % RETRY_TICKS == 0) {
            hauler.getNavigation().moveTo(centre.x, centre.y + 1, centre.z, 1.0);
        }
    }

    private void dump(ServerLevel level) {
        HaulerDepotBlockEntity home = hauler.depot();
        if (home == null) {
            return;   // the entity's own tick handles a missing Depot
        }
        if (hauler.mode() == Mode.WAITING_DEPOT && ++timer % RETRY_TICKS != 0) {
            return;
        }
        boolean all = hauler.dumpInto(home);
        if (!all) {
            // Ruling 23: the Depot is backed up, so wait beside it rather than lose anything.
            hauler.setMode(Mode.WAITING_DEPOT);
            return;
        }
        hauler.setMode(findTarget(level) != null ? Mode.SEEKING : Mode.PARKED_IDLE);
    }

    private void parkedFlat() {
        hauler.getNavigation().stop();
        if (hauler.charge() >= ScrapHaulerEntity.WAKE_AT) {
            hauler.setMode(hauler.cargoFull() ? Mode.RETURNING : Mode.SEEKING);
        }
    }

    /** Ruling 6: it wakes itself. A cheap scan every couple of seconds, not a tick-by-tick search. */
    private void parkedIdle(ServerLevel level) {
        hauler.getNavigation().stop();
        if (++timer % ScrapHaulerEntity.IDLE_SCAN_TICKS != 0) {
            return;
        }
        if (hauler.flat()) {
            hauler.setMode(Mode.PARKED_FLAT);
            return;
        }
        if (findTarget(level) != null) {
            hauler.setMode(Mode.SEEKING);
        }
    }

    private void park(Mode mode) {
        hauler.getNavigation().stop();
        hauler.setMode(mode);
        target = null;
    }

    // ---- targeting -----------------------------------------------------------------------------

    private boolean progressing(double distSq) {
        if (distSq < lastDistanceSq - 0.01) {
            lastDistanceSq = distSq;
            ticksOnTarget = 0;
            return true;
        }
        return ++ticksOnTarget < STUCK_TICKS;
    }

    /**
     * Whether {@code pos} is inside the work area: the Depot's chunk and {@code chunkRadius} rings of
     * chunks around it, on the chunk grid. Static and pure so a unit test can pin the arithmetic,
     * negative coordinates included - {@code >> 4} floors, {@code / 16} does not.
     */
    public static boolean inWorkArea(BlockPos depot, int chunkRadius, BlockPos pos) {
        return Math.abs((pos.getX() >> 4) - (depot.getX() >> 4)) <= chunkRadius
            && Math.abs((pos.getZ() >> 4) - (depot.getZ() >> 4)) <= chunkRadius;
    }

    /**
     * The nearest takeable block to the Hauler inside the Depot's work area.
     *
     * <p><b>Chunks, read off the heightmap</b> (owner, 2026-09-05). The area is a square of chunks
     * around the Depot, and the search reads one column per (x, z) in it: the block under the
     * motion-blocking heightmap. That is 256 cheap lookups per chunk rather than a cube of block
     * reads, and it is also the right shape for what mounds are - piles of {@code FallingBlock}s
     * whose top is always the exposed one, and whose next block is exposed the moment the top goes.
     * A pile under a roof is invisible to it, deliberately: this machine works the surface.
     *
     * <p>Nearest to the HAULER, inside an area around the DEPOT: the site is the Depot's, the order of
     * work is whatever is closest to where it already is.
     */
    public @Nullable BlockPos findTarget(ServerLevel level) {
        BlockPos home = hauler.depotPos();
        HaulerDepotBlockEntity depot = hauler.depot();
        if (home == null || depot == null) {
            return null;
        }
        int r = depot.chunkRadius();
        int cx = home.getX() >> 4;
        int cz = home.getZ() >> 4;
        BlockPos self = hauler.blockPosition();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = cx - r; chunkX <= cx + r; chunkX++) {
            for (int chunkZ = cz - r; chunkZ <= cz + r; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    continue;
                }
                for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
                    for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
                        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
                        if (Math.abs(top - home.getY()) > VERTICAL_REACH) {
                            continue;
                        }
                        for (int y = top; y > top - COLUMN_DEPTH; y--) {
                            pos.set(x, y, z);
                            if (!takeable(level, pos) || blacklist.containsKey(pos)) {
                                continue;
                            }
                            double d = pos.distSqr(self);
                            if (d < bestSq) {
                                bestSq = d;
                                best = pos.immutable();
                            }
                            break;   // the highest takeable block in the column is the one to take
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * Takeable: in the top vacuum band (every pile, fails closed - ruling 20), and not beside a hazard.
     */
    public static boolean takeable(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(RCTags.vacuumable("netherite"))) {
            return false;
        }
        for (Direction side : Direction.values()) {
            BlockPos beside = pos.relative(side);
            var state = level.getBlockState(beside);
            if (state.is(Blocks.FIRE) || state.is(Blocks.LAVA)
                    || level.getFluidState(beside).getType() == RCFluids.LEACHATE.get()) {
                return false;
            }
        }
        return true;
    }
}
