package com.flatts.recompile.content.entity;

import com.flatts.recompile.content.item.GarbageVacuumItem;
import com.flatts.recompile.registry.RCEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A garbage block in flight to a Garbage Vacuum's nozzle (#336).
 *
 * <p><b>An entity, not a block with a renderer.</b> Mining Gadgets animates its mining by swapping the
 * target for a transient block entity whose BER draws the original state shrinking. That is the right
 * picture and the wrong mechanism here: this mod records the Display Pedestal as its ONE
 * BlockEntityRenderer and every other block bakes its model, while entity renderers are already an
 * ordinary thing (the Roach, the Pigeon). So the vacuumed block is a small entity carrying a block
 * state, drawn by {@code VacuumedBlockRenderer} the way vanilla draws a falling block - and because
 * the world block is gone the instant it is taken, whatever was stacked on it starts to fall while
 * this flies, which is the collapse the owner chose.
 *
 * <p>It steers, it does not fall: no gravity, no collision, a speed that ramps up as it closes, a small
 * corkscrew so a stream of them spirals in rather than filing in a line. On arrival the block item goes
 * into the owner's inventory (or drops at their feet when it is full). If the owner is gone - logged
 * off, died, chunk reloaded with nobody there - it drops where it is rather than vanishing: a block
 * that has left the world owes the player an item.
 *
 * <p>Position is the block's CENTRE, and the renderer scales about that point, so shrinking reads as
 * the block being drawn into the hose rather than sinking into the floor.
 */
public class VacuumedBlockEntity extends Entity {

    private static final EntityDataAccessor<BlockState> DATA_STATE =
        SynchedEntityData.defineId(VacuumedBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    private static final EntityDataAccessor<Integer> DATA_OWNER =
        SynchedEntityData.defineId(VacuumedBlockEntity.class, EntityDataSerializers.INT);

    /** A flight that has not arrived by now delivers anyway; the animation must never hold an item hostage. */
    public static final int FLIGHT_TIMEOUT_TICKS = 80;

    /** Close enough to the nozzle to count as swallowed. */
    private static final double ARRIVE_DISTANCE = 0.45;

    /**
     * Further than this and the flight is skipped: the item is delivered on the spot. A vacuum only
     * reaches a few blocks, so this never fires in ordinary play - it exists for an owner who died,
     * warped or logged out between the take and the arrival. Without it the entity chases a target
     * across the world, and the first thing it crosses is the edge of the loaded chunks, where it
     * stops ticking and the block is simply gone. Found by the GameTest, whose mock player stands
     * at world spawn, thousands of blocks from the plot.
     */
    private static final double MAX_FLIGHT_DISTANCE = 24.0;

    /** Speed on the first tick and how much it gains per tick, in blocks. */
    private static final double BASE_SPEED = 0.18;
    private static final double ACCELERATION = 0.035;

    /** Smallest the block draws at, so it is still a block right up to the mouth. */
    private static final float MIN_SCALE = 0.15F;

    /**
     * The owner across a save: entity ids are not stable, so the synced id is re-resolved from this on
     * the first server tick after a load.
     */
    private @Nullable UUID ownerUuid;

    /** Distance to the nozzle when the flight began; the render scale is measured against it. */
    private double startDistance = -1.0;

    public VacuumedBlockEntity(EntityType<? extends VacuumedBlockEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Send the block at {@code from} on its way to {@code owner}'s nozzle. */
    public static VacuumedBlockEntity launch(ServerLevel level, BlockPos from, BlockState state,
            LivingEntity owner) {
        VacuumedBlockEntity flying = new VacuumedBlockEntity(RCEntities.VACUUMED_BLOCK.get(), level);
        flying.setPos(from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5);
        flying.entityData.set(DATA_STATE, state);
        flying.entityData.set(DATA_OWNER, owner.getId());
        flying.ownerUuid = owner.getUUID();
        level.addFreshEntity(flying);
        return flying;
    }

    public BlockState getBlockState() {
        return entityData.get(DATA_STATE);
    }

    /** The entity this is flying to, if it is loaded on this side. */
    public @Nullable LivingEntity owner() {
        return level().getEntity(entityData.get(DATA_OWNER)) instanceof LivingEntity living ? living : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, Blocks.AIR.defaultBlockState());
        builder.define(DATA_OWNER, -1);
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity owner = owner();
        if (owner == null && level() instanceof ServerLevel server && ownerUuid != null) {
            // After a reload the synced id points at nobody. Look the owner up by UUID once.
            if (server.getEntity(ownerUuid) instanceof LivingEntity living) {
                entityData.set(DATA_OWNER, living.getId());
                owner = living;
            }
        }
        if (owner == null || !owner.isAlive()) {
            if (level() instanceof ServerLevel server) {
                dropHere(server);
            }
            return;
        }
        Vec3 target = GarbageVacuumItem.nozzleOf(owner);
        Vec3 here = position();
        Vec3 to = target.subtract(here);
        double distance = to.length();
        if (startDistance < 0.0) {
            startDistance = Math.max(distance, 1.0);
        }
        if (level() instanceof ServerLevel server
                && (distance < ARRIVE_DISTANCE || distance > MAX_FLIGHT_DISTANCE
                    || tickCount > FLIGHT_TIMEOUT_TICKS)) {
            deliver(server, owner, target);
            return;
        }
        if (level().isClientSide()) {
            // THE CLIENT DOES NOT STEER. It is fed positions by the tracker every tick
            // (updateInterval(1)), and a client that also integrates its own motion fights those
            // packets: it advances a step, the next packet snaps it back, and the interpolation the
            // renderer draws is built on a base the client has already overwritten - which reads as a
            // stutter. Vanilla's FallingBlockEntity gets away with ticking on both sides because
            // gravity is deterministic; a block steering toward a moving player is not.
            //
            // startDistance is still computed above on both sides, because renderScale needs it and it
            // is derived from the entity's own first-tick position rather than from the motion.
            return;
        }
        double speed = Math.min(distance, BASE_SPEED + ACCELERATION * tickCount);
        Vec3 step = to.scale(speed / Math.max(distance, 1.0e-4));
        // A sideways wobble that dies out as it closes, so a stream of blocks corkscrews into the
        // hose instead of filing in along a ruler.
        Vec3 side = to.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() > 1.0e-6) {
            side = side.normalize().scale(Math.sin(tickCount * 0.7) * 0.06 * Math.min(1.0, distance));
        } else {
            side = Vec3.ZERO;
        }
        setPos(here.add(step).add(side));
        setDeltaMovement(step);
    }

    /**
     * How large to draw, 1 at launch shrinking towards {@link #MIN_SCALE} at the mouth. Measured off the
     * live distance to the owner rather than off age, so a block taken from close by is already small.
     */
    public float renderScale(float partialTick) {
        LivingEntity owner = owner();
        if (owner == null || startDistance <= 0.0) {
            return 1.0F;
        }
        double distance = GarbageVacuumItem.nozzleOf(owner).distanceTo(getPosition(partialTick));
        float progress = (float) Mth.clamp(1.0 - distance / startDistance, 0.0, 1.0);
        return Mth.lerp(progress, 1.0F, MIN_SCALE);
    }

    private void deliver(ServerLevel level, LivingEntity owner, Vec3 nozzle) {
        ItemStack item = asItem();
        if (!item.isEmpty() && !(owner instanceof Player player && player.getInventory().add(item))) {
            level.addFreshEntity(new ItemEntity(level, owner.getX(), owner.getY(), owner.getZ(), item));
        }
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(),
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 0.7F);
        // One puff. Three filled the bottom of the screen in the dev client; a swallow is a small event.
        level.sendParticles(ParticleTypes.POOF, nozzle.x, nozzle.y, nozzle.z, 1, 0.05, 0.05, 0.05, 0.01);
        discard();
    }

    private void dropHere(ServerLevel level) {
        ItemStack item = asItem();
        if (!item.isEmpty()) {
            Block.popResource(level, blockPosition(), item);
        }
        discard();
    }

    private ItemStack asItem() {
        BlockState state = getBlockState();
        return state.isAir() ? ItemStack.EMPTY : new ItemStack(state.getBlock().asItem());
    }

    // ---- an inert projectile, not a creature ---------------------------------------------

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    // ---- persistence ------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("BlockState", BlockState.CODEC, getBlockState());
        if (ownerUuid != null) {
            output.store("Owner", UUIDUtil.CODEC, ownerUuid);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        entityData.set(DATA_STATE, input.read("BlockState", BlockState.CODEC)
            .orElse(Blocks.AIR.defaultBlockState()));
        ownerUuid = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        // The synced id is meaningless across a load; tick() re-resolves it from the UUID.
        entityData.set(DATA_OWNER, -1);
    }
}
