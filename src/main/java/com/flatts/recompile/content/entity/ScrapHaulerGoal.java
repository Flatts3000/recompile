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
     * reason about.
     *
     * <p><b>HALF the Garbage Vacuum's rate</b> (owner, 2026-09-05). The first pass matched the vacuum
     * exactly, so the two machines took at one speed; the machine that works unattended should not keep
     * pace with the tool a player is standing there holding. Expressed as the vacuum's number doubled
     * rather than as a bare 8, so the relationship survives a retune of either.
     */
    private static final int INTAKE_PERIOD_TICKS =
        com.flatts.recompile.content.item.GarbageVacuumItem.INTAKE_PERIOD_TICKS * 2;

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
            // AFFORDABILITY IS NOT A REASON TO BLACKLIST. FLAT_BELOW is the price of the CHEAPEST
            // takeable block (a trash bag, 40 FE), so a Hauler holding 50 is not "flat" and will still
            // drive to a Block of Garbage it cannot pay the 60 for. take() refuses, and this used to
            // read that as a bad pile and bin a perfectly good one for twenty seconds - then pick the
            // next, fail the same way, and grind through its own field all night with no sun to climb
            // out on, humming the whole time.
            if (hauler.charge() < ScrapHaulerEntity.costOf(level.getBlockState(target))) {
                hauler.setMode(ScrapHaulerEntity.Mode.PARKED_FLAT);
                hauler.getNavigation().stop();
                target = null;
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
     * Whether the column at {@code (x, z)} is inside the work area: the Depot's chunk and
     * {@code chunkRadius} rings of chunks around it, on the chunk grid.
     *
     * <p>Static and pure so a unit test can pin the arithmetic, negative coordinates included -
     * {@code >> 4} floors, {@code / 16} does not.
     */
    public static boolean inWorkArea(BlockPos depot, int chunkRadius, int x, int z) {
        return Math.abs((x >> 4) - (depot.getX() >> 4)) <= chunkRadius
            && Math.abs((z >> 4) - (depot.getZ() >> 4)) <= chunkRadius;
    }

    /** As {@link #inWorkArea(BlockPos, int, int, int)}, for a whole position. */
    public static boolean inWorkArea(BlockPos depot, int chunkRadius, BlockPos pos) {
        return inWorkArea(depot, chunkRadius, pos.getX(), pos.getZ());
    }

    /**
     * Every column on the square ring {@code ring} steps out from {@code (cx, cz)} that also lies
     * inside the box, handed to {@code visit} one at a time.
     *
     * <p>Chebyshev rings: ring 0 is the centre alone, and ring n is the 8n cells on the perimeter of
     * the (2n+1) square.
     *
     * <p><b>Clipped to the box rather than filtered after the fact.</b> The rings are centred on the
     * MACHINE and the work area is a box around the DEPOT, so a Hauler standing in a corner of its
     * area - the ordinary state right after it clears that corner - needs rings reaching the far side,
     * and a square of twice the side has four times the cells. Enumerating those and rejecting them
     * one at a time made the empty-field case about four times MORE expensive than the sweep this
     * replaced, at the very radius the change exists to make usable. Clipping each of the ring's four
     * segments to the box costs nothing and leaves only cells that could actually pay.
     *
     * <p>Extracted and public so {@code ScrapHaulerRingTest} can prove the thing that actually matters
     * about a ring search, which is that it MISSES nothing.
     */
    public static void forEachOnRing(int cx, int cz, int ring, int minX, int maxX, int minZ, int maxZ,
            java.util.function.IntBinaryOperator visit) {
        if (ring == 0) {
            if (cx >= minX && cx <= maxX && cz >= minZ && cz <= maxZ) {
                visit.applyAsInt(cx, cz);
            }
            return;
        }
        int loX = Math.max(cx - ring, minX);
        int hiX = Math.min(cx + ring, maxX);
        // The two rows, corners included.
        for (int z : new int[] {cz - ring, cz + ring}) {
            if (z < minZ || z > maxZ) {
                continue;
            }
            for (int x = loX; x <= hiX; x++) {
                visit.applyAsInt(x, z);
            }
        }
        // The two sides, corners already covered by the rows.
        int loZ = Math.max(cz - ring + 1, minZ);
        int hiZ = Math.min(cz + ring - 1, maxZ);
        for (int x : new int[] {cx - ring, cx + ring}) {
            if (x < minX || x > maxX) {
                continue;
            }
            for (int z = loZ; z <= hiZ; z++) {
                visit.applyAsInt(x, z);
            }
        }
    }

    /**
     * How many rings around {@code self} are needed to reach every column of the work area.
     *
     * <p>Pure and public because this, not the ring walker, is the arithmetic that can silently drop
     * a pile: a bound that forgot one of its four terms would skip everything on one side of the
     * machine and still pass every test about ring shape. {@code ScrapHaulerRingTest} asserts that
     * rings 0 through this number really do cover the whole box.
     */
    public static int ringsToCover(BlockPos home, int chunkRadius, BlockPos self) {
        int minX = ((home.getX() >> 4) - chunkRadius) << 4;
        int maxX = (((home.getX() >> 4) + chunkRadius) << 4) + 15;
        int minZ = ((home.getZ() >> 4) - chunkRadius) << 4;
        int maxZ = (((home.getZ() >> 4) + chunkRadius) << 4) + 15;
        return Math.max(
            Math.max(Math.abs(self.getX() - minX), Math.abs(self.getX() - maxX)),
            Math.max(Math.abs(self.getZ() - minZ), Math.abs(self.getZ() - maxZ)));
    }

    /**
     * The nearest takeable block to the Hauler inside the Depot's work area.
     *
     * <p><b>Searched OUTWARD FROM THE MACHINE</b> (#382). It used to sweep the whole area after every
     * single block taken, with nothing carried between takes: 256 columns a chunk, up to
     * {@link #COLUMN_DEPTH} block reads each, plus six neighbour reads and a fluid check per
     * candidate - about 18,000 lookups a scan at the default radius and 590,000 at the config ceiling,
     * every eight ticks, per Hauler. The work area is a server-cost dial by design; the per-take
     * rescan was the multiplier that made the top of it unusable.
     *
     * <p><b>It returns the same block the full sweep did.</b> Stopping at the first ring that pays
     * would NOT: a ring is a square, so a hit on ring 3 can sit 4.24 blocks away while ring 4 holds one
     * at 4.00, and an earlier draft of this claimed the orderings matched when they did not. A hit at
     * ring r is at least r away, so once the rings pass the best distance found nothing can beat it -
     * and searching that far is usually one ring more than the first hit. Exact, and still cheap.
     *
     * <p>Distance is measured in three dimensions, as the sweep measured it, and the stopping rule
     * still holds: a candidate's 3D distance is never less than its horizontal one, which is never less
     * than its ring.
     *
     * <p><b>Columns, read off the heightmap.</b> One lookup per (x, z) for the block under
     * {@code MOTION_BLOCKING}, then a few blocks down: the top of a column is not always the pile - a
     * leaf, a snow layer, or the test harness's own lid over a plot all sit above it. A pile under a
     * roof is invisible to this, deliberately: the machine works the surface.
     */
    public @Nullable BlockPos findTarget(ServerLevel level) {
        BlockPos home = hauler.depotPos();
        HaulerDepotBlockEntity depot = hauler.depot();
        if (home == null || depot == null) {
            return null;
        }
        int r = depot.chunkRadius();
        BlockPos self = hauler.blockPosition();
        int minX = ((home.getX() >> 4) - r) << 4;
        int maxX = (((home.getX() >> 4) + r) << 4) + 15;
        int minZ = ((home.getZ() >> 4) - r) << 4;
        int maxZ = (((home.getZ() >> 4) + r) << 4) + 15;
        int rings = ringsToCover(home, r, self);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Holders because the ring walker takes a lambda and a lambda cannot assign a local.
        BlockPos[] best = {null};
        double[] bestSq = {Double.MAX_VALUE};
        // One chunk-presence answer reused across a whole row. The sweep asked once per CHUNK; asking
        // once per column would be 73,984 calls a scan at the ceiling, in the loop this change is about.
        long[] lastChunk = {Long.MIN_VALUE};
        boolean[] lastLoaded = {false};

        for (int ring = 0; ring <= rings; ring++) {
            if (best[0] != null && ring > Math.sqrt(bestSq[0])) {
                break;      // no column further out can be nearer than what is already in hand
            }
            forEachOnRing(self.getX(), self.getZ(), ring, minX, maxX, minZ, maxZ, (x, z) -> {
                long chunk = (((long) (x >> 4)) << 32) ^ (z >> 4);
                if (chunk != lastChunk[0]) {
                    lastChunk[0] = chunk;
                    lastLoaded[0] = level.hasChunk(x >> 4, z >> 4);
                }
                if (!lastLoaded[0]) {
                    return 0;
                }
                BlockPos hit = topTakeable(level, home, x, z, pos);
                if (hit != null) {
                    double d = hit.distSqr(self);
                    if (d < bestSq[0]) {
                        bestSq[0] = d;
                        best[0] = hit;
                    }
                }
                return 0;
            });
        }
        return best[0];
    }

    /**
     * The highest takeable block in one column, or null. The column is rejected outright when its
     * surface is further than {@link #VERTICAL_REACH} from the Depot, which is one heightmap read
     * rather than {@link #COLUMN_DEPTH} block reads for the columns that cannot qualify.
     */
    private @Nullable BlockPos topTakeable(ServerLevel level, BlockPos home, int x, int z,
            BlockPos.MutableBlockPos pos) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        if (Math.abs(top - home.getY()) > VERTICAL_REACH) {
            return null;
        }
        for (int y = top; y > top - COLUMN_DEPTH; y--) {
            pos.set(x, y, z);
            if (takeable(level, pos) && !blacklist.containsKey(pos)) {
                return pos.immutable();
            }
        }
        return null;
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
