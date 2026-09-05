package com.flatts.recompile.content.entity;

import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.block.entity.HaulerDepotBlockEntity;
import com.flatts.recompile.content.item.ScrapHaulerItem;
import com.flatts.recompile.content.item.VacuumTier;
import com.flatts.recompile.event.RCAnalytics;
import com.flatts.recompile.registry.RCSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The Scrap Hauler at work (#376, spec {@code docs/scrap_hauler_spec.md}): the deployed form of
 * {@link ScrapHaulerItem}, and this mod's quarry.
 *
 * <p><b>A Mob for the navigation, with the biology switched off</b> (owner, 2026-09-05). It extends
 * {@link PathfinderMob} because that is what makes it path across mound country at all - both existing
 * entities here are Mobs for the same reason - and it then opts out of everything a creature does that
 * a machine should not: it cannot be hurt, cannot fall to its death, cannot drown, cannot be leashed,
 * never despawns, and hostile mobs do not see it. {@code LeachateBlock.sicken} carries an explicit
 * exemption for it, because a mob EFFECT ignores invulnerability and would otherwise apply.
 *
 * <p><b>The failure mode is therefore not death but being STUCK.</b> An indestructible machine can
 * still stand in an eternal tire fire looking broken, strand itself off a ledge, or be entombed by the
 * garbage it just took the foot out of - {@code SortableBlock} is a {@code FallingBlock}. So the
 * pathing maluses refuse fire, lava and water, {@link #unstick} climbs out of anything that lands on
 * it, and Recall <em>teleports</em> rather than paths, because a robot that cannot die but can strand
 * itself needs retrieval that always works.
 *
 * <p><b>It exists exactly once</b>: as an item in a Depot, or as this. The Depot enforces the item
 * half; {@link #remove} tells the Depot when this half is gone for any reason, so a {@code /kill} or a
 * mod removing it cannot leave the slot locked forever. When the Depot itself is gone - broken while
 * this was in an unloaded chunk, the one case the Depot's own auto-recall cannot reach - this folds
 * WITHOUT dropping an item, because the Depot already dropped the one in its slot. Cargo spills at its
 * feet; the machine is not duplicated.
 */
public class ScrapHaulerEntity extends PathfinderMob {

    /** One trip's worth: a stack. First-pass, sized so one Hauler feeds roughly one Trommel (#36). */
    public static final int CARGO_CAPACITY = 64;

    /** A Solar Panel's output, so the machine idles on exactly one panel's worth of sun. */
    public static final int SOLAR_PER_TICK = 2;

    /** Ticks between scans while parked with nothing to do: the self-waking pump, without being a cost. */
    public static final int IDLE_SCAN_TICKS = 40;

    /** Charge below which it parks, and the level it waits for before moving again. */
    public static final int FLAT_BELOW = VacuumTier.costFor(4);
    public static final int WAKE_AT = ScrapHaulerItem.CAPACITY / 10;

    public enum Mode {
        SEEKING, RETURNING, DUMPING, WAITING_DEPOT, PARKED_FLAT, PARKED_IDLE;

        public static Mode of(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : PARKED_IDLE;
        }
    }

    private static final EntityDataAccessor<Integer> DATA_MODE =
        SynchedEntityData.defineId(ScrapHaulerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CARGO =
        SynchedEntityData.defineId(ScrapHaulerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CHARGE =
        SynchedEntityData.defineId(ScrapHaulerEntity.class, EntityDataSerializers.INT);

    /** Client-side, for the model. Driven from the synced mode in {@link #tick}. */
    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState driveAnimation = new AnimationState();
    public final AnimationState pickupAnimation = new AnimationState();

    private @Nullable BlockPos depot;
    private final NonNullList<ItemStack> cargo = NonNullList.withSize(CARGO_CAPACITY, ItemStack.EMPTY);
    private boolean recalling;
    private int pickupFlash;

    public ScrapHaulerEntity(EntityType<? extends ScrapHaulerEntity> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setPersistenceRequired();
        // Refuse rather than merely avoid: a path THROUGH these is worse than no path, because the
        // failure is a machine standing in the fire looking broken, not one that took damage.
        setPathfindingMalus(PathType.FIRE, -1.0F);
        setPathfindingMalus(PathType.FIRE_IN_NEIGHBOR, -1.0F);
        setPathfindingMalus(PathType.DAMAGING, -1.0F);
        setPathfindingMalus(PathType.DAMAGING_IN_NEIGHBOR, -1.0F);
        setPathfindingMalus(PathType.LAVA, -1.0F);
        setPathfindingMalus(PathType.WATER, -1.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 4.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            // The work radius plus a margin, so a target at the edge is inside pathing range.
            .add(Attributes.FOLLOW_RANGE, 48.0)
            // A full block: it climbs slumped garbage rather than stalling at it (spec section 10).
            .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new ScrapHaulerGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, Mode.PARKED_IDLE.ordinal());
        builder.define(DATA_CARGO, 0);
        builder.define(DATA_CHARGE, 0);
    }

    // ---- what it is bound to ---------------------------------------------------------------

    public @Nullable BlockPos depotPos() {
        return depot;
    }

    public void bind(BlockPos depotPos) {
        this.depot = depotPos.immutable();
    }

    /** The Depot this belongs to, if it is loaded and still a Depot. */
    public @Nullable HaulerDepotBlockEntity depot() {
        if (depot == null || !level().isLoaded(depot)) {
            return null;
        }
        return level().getBlockEntity(depot) instanceof HaulerDepotBlockEntity be ? be : null;
    }

    // ---- state --------------------------------------------------------------------------------

    public Mode mode() {
        return Mode.of(entityData.get(DATA_MODE));
    }

    public void setMode(Mode mode) {
        entityData.set(DATA_MODE, mode.ordinal());
    }

    public int charge() {
        return entityData.get(DATA_CHARGE);
    }

    public void setCharge(int charge) {
        entityData.set(DATA_CHARGE, Math.max(0, Math.min(ScrapHaulerItem.CAPACITY, charge)));
    }

    public int cargoCount() {
        return entityData.get(DATA_CARGO);
    }

    public boolean cargoFull() {
        return cargoCount() >= CARGO_CAPACITY;
    }

    public boolean cargoEmpty() {
        return cargoCount() == 0;
    }

    private void recount() {
        int n = 0;
        for (ItemStack stack : cargo) {
            n += stack.getCount();
        }
        entityData.set(DATA_CARGO, n);
    }

    /** What it is carrying, as stacks. A copy; the hold is private. */
    public List<ItemStack> cargo() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : cargo) {
            if (!stack.isEmpty()) {
                out.add(stack.copy());
            }
        }
        return out;
    }

    /** Put a stack in the hold; what did not fit comes back. */
    public ItemStack addCargo(ItemStack stack) {
        ItemStack rest = stack.copy();
        int room = CARGO_CAPACITY - cargoCount();
        if (room <= 0 || rest.isEmpty()) {
            return rest;
        }
        int take = Math.min(room, rest.getCount());
        ItemStack taking = rest.copyWithCount(take);
        rest.shrink(take);
        for (int i = 0; i < cargo.size() && !taking.isEmpty(); i++) {
            ItemStack slot = cargo.get(i);
            if (slot.isEmpty()) {
                cargo.set(i, taking);
                taking = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(slot, taking)) {
                int fit = Math.min(slot.getMaxStackSize() - slot.getCount(), taking.getCount());
                slot.grow(fit);
                taking.shrink(fit);
            }
        }
        recount();
        return rest;
    }

    /** Empty the hold into {@code sink}, one stack at a time; what it refuses stays aboard. */
    public boolean dumpInto(HaulerDepotBlockEntity sink) {
        boolean all = true;
        for (int i = 0; i < cargo.size(); i++) {
            ItemStack stack = cargo.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = sink.receive(stack);
            cargo.set(i, rest);
            if (!rest.isEmpty()) {
                all = false;
            }
        }
        recount();
        return all;
    }

    private void spillCargo() {
        for (int i = 0; i < cargo.size(); i++) {
            ItemStack stack = cargo.get(i);
            if (!stack.isEmpty()) {
                Block.popResource(level(), blockPosition(), stack);
                cargo.set(i, ItemStack.EMPTY);
            }
        }
        recount();
    }

    // ---- the work ------------------------------------------------------------------------------

    /** What taking {@code state} would cost: the vacuum's price, so the two machines cannot disagree. */
    public static int costOf(BlockState state) {
        return VacuumTier.costFor(SortableBlock.sortRolls(state.getBlock().asItem()));
    }

    /**
     * Take the block at {@code pos} into the hold. The caller has already decided it is takeable.
     *
     * <p>A plain {@code removeBlock}, the vacuum's reasoning: it fires neighbour updates, which is what
     * lets a {@code FallingBlock} above it fall. Suppress those and a mound hangs in the air - and the
     * mound coming down on this machine is the case {@link #unstick} exists for.
     */
    public boolean take(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        int cost = costOf(state);
        if (cost <= 0 || charge() < cost) {
            return false;
        }
        ItemStack item = new ItemStack(state.getBlock().asItem());
        if (!addCargo(item).isEmpty()) {
            return false;   // no room; caller should be returning, not taking
        }
        setCharge(charge() - cost);
        level.removeBlock(pos, false);
        RCAnalytics.broke(state.getBlock());
        VacuumedBlockEntity.launch(level, pos, state, this);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, pos, RCSounds.HAULER_PICKUP.get(), SoundSource.NEUTRAL, 0.8F, 1.0F);
        pickupFlash = 12;
        return true;
    }

    /** Whether a full trip's next action is affordable; below this it parks in the sun. */
    public boolean flat() {
        return charge() < FLAT_BELOW;
    }

    // ---- ticking -------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            Mode mode = mode();
            boolean moving = getDeltaMovement().horizontalDistanceSqr() > 1.0E-5;
            driveAnimation.animateWhen(moving && mode != Mode.PARKED_FLAT && mode != Mode.PARKED_IDLE, tickCount);
            idleAnimation.animateWhen(!moving, tickCount);
            pickupAnimation.animateWhen(mode == Mode.SEEKING && pickupFlash > 0, tickCount);
            if (pickupFlash > 0) {
                pickupFlash--;
            }
            return;
        }
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (pickupFlash > 0) {
            pickupFlash--;
        }
        trickle(level);
        unstick(level);
        HaulerDepotBlockEntity home = depot();
        if (home == null) {
            if (depot != null && level.isLoaded(depot)) {
                // The Depot is loaded and it is not a Depot any more: it was broken while this was
                // somewhere the auto-recall could not reach. Fold without an item; see the class javadoc.
                foldWithoutDepot();
            }
            return;
        }
        if (!home.owns(getUUID())) {
            // A Depot is there, but not the one that deployed this. The one that did was broken
            // while this was unloaded and dropped its item; a replacement placed on the same spot
            // must not inherit a second machine. Same fold, same reason: the item already exists.
            foldWithoutDepot();
            return;
        }
        if (home.recallRequested()) {
            recallTo(home);
        }
    }

    /**
     * The solar trickle. Sky light minus the world's darkening, the Solar Panel's own expression, so
     * night, dawn, dusk and weather all come out of one number rather than an {@code isDay} branch.
     */
    private void trickle(ServerLevel level) {
        BlockPos at = blockPosition();
        if (!level.canSeeSky(at)) {
            return;
        }
        int daylight = level.getBrightness(LightLayer.SKY, at) - level.getSkyDarken();
        if (daylight >= 12 && charge() < ScrapHaulerItem.CAPACITY) {
            setCharge(charge() + SOLAR_PER_TICK);
        }
    }

    /**
     * Climb out of anything that has landed on it.
     *
     * <p>Taking the foot of a stack collapses it, and a landed garbage block cannot hurt this machine
     * but can entomb it - the sharpest requirement the indestructible ruling created. If the cell it
     * occupies has become solid, move to the top of the column rather than sit inside it.
     */
    private void unstick(ServerLevel level) {
        BlockPos at = blockPosition();
        if (!level.getBlockState(at).isSolid()) {
            return;
        }
        BlockPos.MutableBlockPos probe = at.mutable();
        for (int i = 0; i < 8; i++) {
            probe.move(0, 1, 0);
            if (!level.getBlockState(probe).isSolid()) {
                teleportTo(probe.getX() + 0.5, probe.getY(), probe.getZ() + 0.5);
                getNavigation().stop();
                return;
            }
        }
    }

    // ---- the transformations ------------------------------------------------------------------

    /**
     * Go home, now: dump what fits, hand the charge back to the item in the Depot, and cease to exist.
     *
     * <p>Teleports rather than paths, deliberately. This is the retrieval that has to work when the
     * machine is stuck, and walking home is exactly what has already failed if it is.
     */
    public void recallTo(HaulerDepotBlockEntity home) {
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        recalling = true;
        dumpInto(home);
        spillCargoAt(level, home.getBlockPos());
        home.onRecalled(this);
        level.playSound(null, home.getBlockPos(), RCSounds.HAULER_RECALL.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        discard();
    }

    private void spillCargoAt(ServerLevel level, BlockPos at) {
        for (int i = 0; i < cargo.size(); i++) {
            ItemStack stack = cargo.get(i);
            if (!stack.isEmpty()) {
                Block.popResource(level, at.above(), stack);
                cargo.set(i, ItemStack.EMPTY);
            }
        }
        recount();
    }

    private void foldWithoutDepot() {
        recalling = true;
        spillCargo();
        discard();
    }

    /**
     * Whatever removes this - a recall, a {@code /kill}, another mod - the Depot hears about it, so
     * its slot is never locked against a machine that no longer exists.
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!recalling && !level().isClientSide()) {
            HaulerDepotBlockEntity home = depot();
            if (home != null) {
                home.onHaulerGone(getUUID());
            }
        }
        super.remove(reason);
    }

    // ---- the biology, switched off ------------------------------------------------------------

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        // Only what vanilla lets through an invulnerable entity anyway - /kill and the void - so an
        // operator can still remove one. Everything else, creepers included, is refused.
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(level, source);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    /**
     * No death animation. The only thing that can get past {@link #isInvulnerableTo} is a
     * {@code /kill} or the void, and a machine does not lie down for twenty ticks first: it is
     * removed on the spot, which is what lets {@link #remove} unlock the Depot on the same tick.
     */
    @Override
    protected void tickDeath() {
        remove(RemovalReason.KILLED);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    // ---- persistence --------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (depot != null) {
            output.putInt("depot_x", depot.getX());
            output.putInt("depot_y", depot.getY());
            output.putInt("depot_z", depot.getZ());
        }
        output.putInt("charge", charge());
        output.putInt("mode", mode().ordinal());
        ContainerHelper.saveAllItems(output.child("cargo"), cargo);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int x = input.getIntOr("depot_x", Integer.MIN_VALUE);
        if (x != Integer.MIN_VALUE) {
            depot = new BlockPos(x, input.getIntOr("depot_y", 0), input.getIntOr("depot_z", 0));
        }
        setCharge(input.getIntOr("charge", 0));
        setMode(Mode.of(input.getIntOr("mode", Mode.PARKED_IDLE.ordinal())));
        input.child("cargo").ifPresent(c -> ContainerHelper.loadAllItems(c, cargo));
        recount();
    }
}
