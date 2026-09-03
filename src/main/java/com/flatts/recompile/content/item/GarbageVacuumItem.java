package com.flatts.recompile.content.item;

import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.content.entity.VacuumedBlockEntity;
import com.flatts.recompile.event.RCAnalytics;
import com.flatts.recompile.registry.RCDataComponents;
import com.flatts.recompile.registry.RCTags;
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
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

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
 * <p><b>Tier decides reach, buffer and WHAT IT IS RATED FOR</b> ({@link VacuumTier}, owner
 * 2026-09-03). The bands follow the regions: copper handles household waste, iron adds the demolition
 * yard, diamond the radioactive dump, netherite the compacted depths. Membership is a block tag per
 * tier, {@code #recompile:vacuumable/<tier>}, each including the band below it - so the ladder lives in
 * data and a pack widens a band without a mod release. See {@link #canTake}.
 *
 * <p>A refusal SAYS SO. Aiming a copper vacuum at mill tailings names the pile in the action bar
 * rather than doing nothing, because "nothing happens" and "you need a better vacuum" look identical
 * from the player's side and only one of them is learnable.
 */
public class GarbageVacuumItem extends Item {

    /** One block every four ticks: five a second, which is a slump rather than a trickle. */
    public static final int INTAKE_PERIOD_TICKS = 4;

    /** How far in front of the player the intake volume is centred. */
    public static final double REACH = 5.0;

    /** The bow's "indefinitely": use ends when the player lets go or the charge runs out. */
    private static final int USE_DURATION = 72_000;

    private static final int SOUND_PERIOD_TICKS = 12;

    /** Dust motes per tick. Two, not four: see {@link #clientParticles}. */
    private static final int DUST_STREAMS = 2;

    /**
     * How far short of the nozzle the dust stops, in blocks.
     *
     * <p>Bigger than the nozzle's own distance from the eyes on purpose, so the stream thins out well
     * before the lens instead of piling up on it.
     */
    private static final double DUST_STOP = 2.0;

    /** What one call to {@link #intakeOnce} did. */
    public enum Intake {
        /** A block left the world and is on its way to the nozzle. */
        TOOK,
        /** Nothing takeable in the volume. */
        NOTHING_IN_RANGE,
        /** There is a pile in range, and this tier is not rated for it. */
        TOO_TOUGH,
        /** Something was there and the charge could not cover it. */
        FLAT
    }

    private final VacuumTier tier;

    /**
     * What this tier is rated to take. Resolved once, because {@link #canTake} is asked per block per
     * tick over a cube - 1,331 cells at netherite's radius.
     */
    private final TagKey<Block> accepts;

    public GarbageVacuumItem(Properties properties, VacuumTier tier) {
        super(properties);
        this.tier = tier;
        this.accepts = RCTags.vacuumable(tier.name());
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
     * Right-clicking AT A BLOCK. Needed as well as {@link #use} because vanilla resolves a click
     * block-first: with the crosshair on a pile the chain reaches
     * {@code ItemStack.useOn} and stops there, and {@code use} is only called when the click hit
     * nothing. {@code SortableBlock} passes rather than hand-sorting so this is reached at all; see
     * the note there. Both paths funnel into {@link #beginVacuuming}, and this one consuming the
     * action is what stops the click being paid for twice.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return beginVacuuming(context.getLevel(), player, context.getHand());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return beginVacuuming(level, player, hand);
    }

    /**
     * A tap takes one block; a hold keeps taking.
     *
     * <p>The first intake happens HERE, on the click, not on the first use tick. A tap starts and
     * releases the use within one tick, so {@code onUseTick} never runs for it - measured through
     * devbridge, whose {@code use} verb is exactly a tap, and true of a real quick click too. A tool
     * that does nothing on a click and only works when held reads as broken for the first second of
     * owning it. The use loop then skips its tick-zero intake so a hold does not take two at once.
     */
    private InteractionResult beginVacuuming(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof GarbageVacuumItem)) {
            return InteractionResult.PASS;
        }
        if (!player.hasInfiniteMaterials() && charge(stack) <= 0) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.recompile.vacuum_flat"));
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            Vec3 aim = aimPoint(player);
            if (intakeOnce(server, player, stack, aim) == Intake.TOO_TOUGH) {
                // Do not start the hold: there is nothing here this tier can do, and a running vacuum
                // that takes nothing is the same silent failure the message exists to prevent.
                sayTooTough(this, player, aim);
                return InteractionResult.CONSUME;
            }
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    /** Name the pile this tier could not take, so the ladder is learnable from the tool. */
    private static void sayTooTough(GarbageVacuumItem vacuum, Player player, Vec3 aim) {
        BlockState blocked = vacuum.blockedInRange(player.level(), aim);
        if (blocked != null) {
            player.sendOverlayMessage(Component.translatable("message.recompile.vacuum_too_tough",
                Component.translatable(blocked.getBlock().getDescriptionId())));
        }
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
            if (elapsed > 0 && elapsed % INTAKE_PERIOD_TICKS == 0) {
                Intake result = intakeOnce(server, player, stack, aim);
                if (result == Intake.FLAT) {
                    player.releaseUsingItem();
                    player.sendOverlayMessage(Component.translatable("message.recompile.vacuum_flat"));
                } else if (result == Intake.TOO_TOUGH) {
                    player.releaseUsingItem();
                    sayTooTough(this, player, aim);
                }
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
        List<BlockPos> candidates = vacuum.candidates(level, player, aim);
        if (candidates.isEmpty()) {
            return vacuum.blockedInRange(level, aim) == null
                ? Intake.NOTHING_IN_RANGE
                : Intake.TOO_TOUGH;
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
    public List<BlockPos> candidates(Level level, Player player, Vec3 aim) {
        BlockPos centre = BlockPos.containing(aim);
        int r = tier.radius();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            if (canTake(level.getBlockState(pos)) && mayTake(player, level, pos)) {
                found.add(pos.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(aim)));
        return found;
    }

    /**
     * Whether this player is allowed to remove the block at {@code pos} at all.
     *
     * <p><b>Nothing upstream checks this for us.</b> A vacuum removes blocks from {@code use} and
     * {@code useOn}, and neither path in {@code ServerPlayerGameMode} performs a build-permission test
     * - those live on the DIG path and on {@code BlockItem} placement. So without this an
     * adventure-mode player, or anyone standing in a server's spawn protection or someone else's
     * claim, could hold right-click and strip every pile within reach. Every other tool in this mod is
     * safe by accident, because each acts on a block vanilla had already permitted.
     *
     * <p>Both halves are needed and neither implies the other: {@code mayBuild} is the gamemode
     * (adventure and spectator say no), and {@code mayInteract} is the world's own answer, which
     * {@code ServerPlayer} overrides with the spawn-protection check.
     */
    private static boolean mayTake(Player player, Level level, BlockPos pos) {
        if (!player.mayBuild()) {
            return false;
        }
        return !(level instanceof ServerLevel server) || player.mayInteract(server, pos);
    }

    /**
     * A pile this tier is rated for. Type is decided by class ({@code instanceof SortableBlock}, never
     * a list); the tier band by a data tag, {@code #recompile:vacuumable/<tier>}.
     *
     * <p><b>Both halves are load-bearing and neither implies the other.</b> The class check keeps the
     * tool honest about what it is for - it takes pick-through piles and nothing else, so no tag can
     * turn it into a block-breaker. The tag is what makes the ladder a ladder, and it is data, so a
     * pack can widen a band without a mod release.
     *
     * <p><b>Fails CLOSED:</b> a sortable in no band is takeable by nothing. That is the safe direction
     * and it is not left to trust - {@code every_sortable_block_is_in_a_vacuum_band} fails the build on
     * an untagged pile, because the alternative is a new pile the vacuum silently ignores forever.
     *
     * <p>This used to gate on the tier's {@code ToolMaterial} instead, asking whether the material
     * would deny drops. That check could never fire, because no sortable carries a
     * {@code needs_*_tool} tag - and if one ever had, it would have been a second invisible gate
     * sitting beside this one.
     */
    public boolean canTake(BlockState state) {
        return state.getBlock() instanceof SortableBlock && state.is(accepts);
    }

    /**
     * A pile in range this tier is NOT rated for, if there is one.
     *
     * <p>Exists so a refusal can say why. Without it, aiming a copper vacuum at mill tailings is
     * indistinguishable from aiming it at nothing: the trigger does nothing, and the only thing the
     * player learns is that the tool is broken. Same call {@code RCHarvestGate} made about digging a
     * pile with the wrong tool, for the same reason - a rule the player cannot see is one they cannot
     * learn.
     */
    public @Nullable BlockState blockedInRange(Level level, Vec3 aim) {
        BlockPos centre = BlockPos.containing(aim);
        int r = tier.radius();
        // NEAREST to the aim point, not first in iteration order. betweenClosed walks from the
        // -r,-r,-r corner, so the first match is the corner-most pile - and a message that names a
        // Waste Drum behind you when you are pointing at Mill Tailings teaches the wrong rung of the
        // ladder, which is the one job this message has.
        BlockState nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SortableBlock && !canTake(state)) {
                double d = pos.distToCenterSqr(aim);
                if (d < best) {
                    best = d;
                    nearest = state;
                }
            }
        }
        return nearest;
    }

    /**
     * Where the intake is centred: the block face in front of the player, or {@link #REACH} into the air.
     *
     * <p><b>partialTicks 1.0F means "now".</b> {@code Entity.getXRot(float)} special-cases exactly 1.0F
     * and otherwise lerps from {@code xRotO}, so passing 0.0F aims at the PREVIOUS tick's rotation -
     * the intake volume and the dust stream both trail the crosshair by a tick while turning, which at
     * radius 2 is enough to take a pile the player has already turned away from. The gametests could
     * not see it: a freshly-made mock player has {@code xRotO == xRot}.
     */
    public static Vec3 aimPoint(Player player) {
        HitResult hit = player.pick(REACH, 1.0F, false);
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
     * Client side only: dust lifting off the piles in range and drawn toward the nozzle.
     *
     * <p><b>The stream stays out at the far end and never reaches the camera</b>, which is the whole
     * shape of this method rather than a detail of it. The first version aimed everything at the
     * nozzle and added a {@code POOF} puff at the mouth for "the vacuum is on": the nozzle is about a
     * block from the eyes, so a converging stream plus a white puff piled up ON the lens and hid the
     * thing being vacuumed - reported on the first playtest, and clearly visible in the dev-client
     * screenshots as a white blob over the lower right of the frame. So: the puff is gone entirely,
     * the count is two rather than four, and each particle is aimed at a point {@link #DUST_STOP}
     * blocks short of the nozzle, along the same line. It still reads as suction, because what sells
     * it is dust leaving the pile and converging, not dust arriving.
     *
     * <p>Block dust rather than a generic particle for the same reason the flying block is the real
     * block: the colour of what you are clearing is the feedback.
     */
    private void clientParticles(Level level, Player player, Vec3 aim) {
        RandomSource random = level.getRandom();
        Vec3 nozzle = nozzleOf(player);
        // SAMPLED, not enumerated. This used to call candidates(), which reads up to 1,331 block states
        // and then sorts them by distance - twenty times a second, for the whole of a hold, to pick two
        // of them at random. The sort was pure waste (a random pick does not care about order) and so
        // was most of the scan. Throwing darts at the cube costs a handful of lookups and looks the
        // same, because the dust only has to come off SOME pile in range.
        int r = tier.radius();
        BlockPos centre = BlockPos.containing(aim);
        for (int i = 0; i < DUST_STREAMS; i++) {
            BlockPos pos = null;
            for (int attempt = 0; attempt < 12 && pos == null; attempt++) {
                BlockPos candidate = centre.offset(
                    random.nextInt(2 * r + 1) - r,
                    random.nextInt(2 * r + 1) - r,
                    random.nextInt(2 * r + 1) - r);
                if (canTake(level.getBlockState(candidate))) {
                    pos = candidate;
                }
            }
            if (pos == null) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            Vec3 from = Vec3.atLowerCornerOf(pos)
                .add(random.nextDouble(), random.nextDouble(), random.nextDouble());
            Vec3 toNozzle = nozzle.subtract(from);
            double travel = toNozzle.length() - DUST_STOP;
            if (travel <= 0.0) {
                continue;   // already inside the stop radius: drawing it would be drawing it on the lens
            }
            Vec3 velocity = toNozzle.normalize().scale(Math.min(travel, 0.45));
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
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
        lines.accept(Component.translatable("tooltip.recompile.vacuum_band." + tier.name()));
    }
}
