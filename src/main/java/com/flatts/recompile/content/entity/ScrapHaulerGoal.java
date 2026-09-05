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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Hauler's one behaviour: the fetch-and-return loop from the spec, as a state machine.
 *
 * <p>Seek a takeable block inside the Depot's work radius, take it, repeat until the hold is full or
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

    /** Ruling 5: a radius around the Depot, not around the Hauler. */
    public static final int WORK_RADIUS = 16;

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
     * The nearest takeable block to the Hauler inside the Depot's radius that a path can reach.
     *
     * <p>Nearest to the HAULER, inside a radius around the DEPOT: the site is the Depot's, the order of
     * work is whatever is closest to where it already is.
     */
    public @Nullable BlockPos findTarget(ServerLevel level) {
        BlockPos home = hauler.depotPos();
        if (home == null) {
            return null;
        }
        BlockPos self = hauler.blockPosition();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        int r = WORK_RADIUS;
        for (BlockPos pos : BlockPos.betweenClosed(home.offset(-r, -r, -r), home.offset(r, r, r))) {
            if (!takeable(level, pos) || blacklist.containsKey(pos)) {
                continue;
            }
            double d = pos.distSqr(self);
            if (d < bestSq) {
                bestSq = d;
                best = pos.immutable();
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
