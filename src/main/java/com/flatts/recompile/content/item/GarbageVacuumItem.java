package com.flatts.recompile.content.item;

import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.entity.VacuumedBlockEntity;
import com.flatts.recompile.event.RCAnalytics;
import com.flatts.recompile.registry.RCDataComponents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Garbage Vacuum (#336): hold right-click and garbage blocks in front of you leave the world and
 * fly into the nozzle, several a second, for FE.
 *
 * <p><b>It takes BLOCKS, not items</b> (owner, 2026-09-03; the magnet is the item half). And it does
 * not automate the pick-through loop: breaking a garbage block already drops the block itself, so
 * this only makes collecting fast. Sorting still happens afterwards, by hand or in a Trommel, and the
 * {@code sorted} progress of a half-worked block is discarded - a block item carries no blockstate,
 * and that loss is accepted rather than carried in a component (spec, {@code docs/garbage_vacuum_spec.md}).
 *
 * <p><b>The animation is the design.</b> Slime Rancher's vacpack, in this game's idiom: the item model
 * switches to a running overlay while in use ({@code using_item} in the client item definition, the
 * bow's mechanism), block dust streams from the intake volume into the nozzle, and each block taken
 * becomes a {@link VacuumedBlockEntity} that arcs into the hose and shrinks. Because the block leaves
 * the world the instant it is taken, everything stacked on it starts to fall - which is the collapse
 * the owner chose over a top-down peel.
 *
 * <p><b>Charge is a data component read by two doors.</b> {@link #charge} and {@link #setCharge}
 * touch {@code VACUUM_CHARGE} directly for the drain; the Charging Station and any other mod reach the
 * same component through {@code Capabilities.Energy.ITEM}, registered per tier in
 * {@code RCBlockEntities}. Both read one number, so they cannot disagree.
 *
 * <p>Tier decides reach and buffer ({@link VacuumTier}) and, through its {@link ToolMaterial}, which
 * gated piles it may take: a sortable that {@code requiresCorrectToolForDrops} is skipped when the
 * tier's material would deny drops on it. Today no sortable carries a {@code needs_*_tool} tag, so every
 * tier takes every pile; the check exists so that stops being true the day a pile gains one.
 */
public class GarbageVacuumItem extends Item {

    /** One block every four ticks: five a second, which is a slump rather than a trickle. */
    public static final int INTAKE_PERIOD_TICKS = 4;

    /** How far in front of the player the intake volume is centred. */
    public static final double REACH = 5.0;

    /** The bow's "indefinitely": use ends when the player lets go or the charge runs out. */
    private static final int USE_DURATION = 72_000;

    private static final int SOUND_PERIOD_TICKS = 12;

    /** What one call to {@link #intakeOnce} did. */
    public enum Intake {
        /** A block left the world and is on its way to the nozzle. */
        TOOK,
        /** Nothing takeable in the volume. */
        NOTHING_IN_RANGE,
        /** Something was there and the charge could not cover it. */
        FLAT
    }

    private final VacuumTier tier;
    private final ToolMaterial material;

    public GarbageVacuumItem(Properties properties, VacuumTier tier, ToolMaterial material) {
        super(properties);
        this.tier = tier;
        this.material = material;
    }

    public VacuumTier tier() {
        return tier;
    }

    // ---- charge -------------------------------------------------------------------------

    public static int charge(ItemStack stack) {
        return stack.getOrDefault(RCDataComponents.VACUUM_CHARGE.get(), 0);
    }

    public static void setCharge(ItemStack stack, int charge) {
        stack.set(RCDataComponents.VACUUM_CHARGE.get(), Math.max(0, charge));
    }

    public static int capacityOf(ItemStack stack) {
        return stack.getItem() instanceof GarbageVacuumItem vacuum ? vacuum.tier.capacity() : 0;
    }

    // ---- use ------------------------------------------------------------------------------

    /**
     * A tap takes one block; a hold keeps taking.
     *
     * <p>The first intake happens HERE, on the click, not on the first use tick. A tap starts and
     * releases the use within one tick, so {@code onUseTick} never runs for it - measured through
     * devbridge, whose {@code use} verb is exactly a tap, and true of a real quick click too. A tool
     * that does nothing on a click and only works when held reads as broken for the first second of
     * owning it. The use loop then skips its tick-zero intake so a hold does not take two at once.
     */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.hasInfiniteMaterials() && charge(stack) <= 0) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.recompile.vacuum_flat"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            intakeOnce(server, player, stack, aimPoint(player));
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return USE_DURATION;
    }

    /** NONE: the model animates itself while in use, and a bow-draw or brush-sweep pose would fight it. */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
        if (!(user instanceof Player player)) {
            user.releaseUsingItem();
            return;
        }
        int elapsed = USE_DURATION - ticksRemaining;
        Vec3 aim = aimPoint(player);
        if (level instanceof ServerLevel server) {
            if (elapsed % SOUND_PERIOD_TICKS == 0) {
                server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.35F, 0.85F);
            }
            // elapsed 0 is the click itself, which use() has already paid for.
            if (elapsed > 0 && elapsed % INTAKE_PERIOD_TICKS == 0
                    && intakeOnce(server, player, stack, aim) == Intake.FLAT) {
                player.releaseUsingItem();
                player.sendOverlayMessage(Component.translatable("message.recompile.vacuum_flat"));
            }
        } else {
            clientParticles(level, player, aim);
        }
    }

    /**
     * Take one block, if there is one to take and charge to cover it. The static entry point the
     * GameTests drive, the same way {@code SortableBlock.sortOnce} is.
     *
     * <p>Nearest to the aim point first, so where the player points decides what goes: aim at the foot
     * of a mound and it slumps, aim at the crown and it peels. The block is removed with a plain
     * {@code removeBlock}, which fires neighbour updates, which is what makes a {@code FallingBlock}
     * above it start to fall - suppress those and the mound hangs in the air.
     */
    public static Intake intakeOnce(ServerLevel level, Player player, ItemStack stack, Vec3 aim) {
        if (!(stack.getItem() instanceof GarbageVacuumItem vacuum)) {
            return Intake.NOTHING_IN_RANGE;
        }
        List<BlockPos> candidates = vacuum.candidates(level, aim);
        if (candidates.isEmpty()) {
            return Intake.NOTHING_IN_RANGE;
        }
        BlockPos pos = candidates.get(0);
        BlockState state = level.getBlockState(pos);
        int cost = VacuumTier.costFor(SortableBlock.sortRolls(state.getBlock().asItem()));
        boolean free = player.hasInfiniteMaterials();
        if (!free && charge(stack) < cost) {
            return Intake.FLAT;
        }
        if (!free) {
            setCharge(stack, charge(stack) - cost);
        }
        level.removeBlock(pos, false);
        // The analytics count a vacuumed block as a BREAK, the same row a shovel writes: both remove a
        // block without rolling its pull table, and the split that row exists to measure is
        // "cleared" against "picked through".
        RCAnalytics.broke(state.getBlock());
        VacuumedBlockEntity.launch(level, pos, state, player);
        level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.8F, 0.6F);
        return Intake.TOOK;
    }

    /** Every takeable pile in the intake volume, nearest to the aim point first. */
    public List<BlockPos> candidates(Level level, Vec3 aim) {
        BlockPos centre = BlockPos.containing(aim);
        int r = tier.radius();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (canTake(level.getBlockState(pos))) {
                found.add(pos.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(aim)));
        return found;
    }

    /**
     * A sortable pile this tier may take. Type is decided by class ({@code instanceof SortableBlock},
     * never a list); tier by whether the material would deny drops on a gated block.
     */
    public boolean canTake(BlockState state) {
        if (!(state.getBlock() instanceof SortableBlock)) {
            return false;
        }
        return !state.requiresCorrectToolForDrops() || !state.is(material.incorrectBlocksForDrops());
    }

    /** Where the intake is centred: the block face in front of the player, or {@link #REACH} into the air. */
    public static Vec3 aimPoint(Player player) {
        HitResult hit = player.pick(REACH, 0.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        return player.getEyePosition().add(player.getLookAngle().scale(REACH));
    }

    /**
     * The mouth of the hose, in world space: a little in front of the eyes, offset to the side the
     * tool is held on, below the eye line. Shared by the flight of a {@link VacuumedBlockEntity} and
     * the particle stream, so both converge on the same point.
     */
    public static Vec3 nozzleOf(LivingEntity owner) {
        Vec3 look = owner.getLookAngle();
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0e-6) {
            right = new Vec3(1.0, 0.0, 0.0);
        }
        right = right.normalize();
        boolean onRight = (owner.getUsedItemHand() == InteractionHand.MAIN_HAND)
            == (owner.getMainArm() == HumanoidArm.RIGHT);
        return owner.getEyePosition()
            .add(look.scale(0.9))
            .add(right.scale(onRight ? 0.35 : -0.35))
            .subtract(0.0, 0.35, 0.0);
    }

    /**
     * Client side only: dust off the piles in range streaming into the nozzle, and a puff of air at the
     * mouth whether or not there is anything to take - a vacuum that is on makes noise and moves air.
     */
    private void clientParticles(Level level, Player player, Vec3 aim) {
        RandomSource random = level.getRandom();
        Vec3 nozzle = nozzleOf(player);
        List<BlockPos> candidates = candidates(level, aim);
        int streams = Math.min(candidates.size(), 4);
        for (int i = 0; i < streams; i++) {
            BlockPos pos = candidates.get(random.nextInt(candidates.size()));
            BlockState state = level.getBlockState(pos);
            Vec3 from = Vec3.atLowerCornerOf(pos)
                .add(random.nextDouble(), random.nextDouble(), random.nextDouble());
            Vec3 velocity = nozzle.subtract(from).normalize().scale(0.45);
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
        }
        if (random.nextInt(3) == 0) {
            Vec3 from = nozzle.add(player.getLookAngle().scale(1.2))
                .add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6,
                    (random.nextDouble() - 0.5) * 0.6);
            Vec3 velocity = nozzle.subtract(from).normalize().scale(0.25);
            level.addParticle(ParticleTypes.POOF, from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
        }
    }

    // ---- bar + tooltip ------------------------------------------------------------------

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return tier.barWidth(charge(stack));
    }

    /** Energy blue, not durability green: the bar is a charge gauge and reads as one at a glance. */
    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3FB8FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.translatable("tooltip.recompile.energy_stored",
            String.format("%,d", charge(stack)), String.format("%,d", tier.capacity())));
        lines.accept(Component.translatable("tooltip.recompile.vacuum_radius", tier.radius()));
    }
}
