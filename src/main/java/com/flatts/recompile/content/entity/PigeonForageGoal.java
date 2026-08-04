package com.flatts.recompile.content.entity;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.content.block.SortableBlock;
import com.flatts.recompile.registry.RCTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * A pigeon walks to a pile of garbage and pecks at it, and once in a while pulls something out.
 *
 * <p><b>It does not sort the block, and that is the whole safety of it.</b> A real pull advances the
 * {@code sorted} blockstate and eventually crumbles the block - so a pigeon that really sorted would
 * slowly eat the player's dump while they were away, which is the one thing an ambient mob must never
 * do. This rolls its own small table and leaves the block exactly as it found it: the peck is additive,
 * never destructive.
 *
 * <p><b>One peck per visit</b> (owner, 2026-08-04). The first version kept the goal running once it
 * arrived, so a bird that reached a pile pecked every two seconds for as long as it stood there and
 * produced a small heap of loot in under a minute. The cooldown existed but only gated <em>starting</em>,
 * which is the kind of bug that looks like a tuning problem and is not. The goal now ends on the peck,
 * so the cooldown is what a player actually experiences.
 *
 * <p><b>It can only ever find what the pile itself would have given you, and that is a gating rule</b>
 * (owner, 2026-08-04). A pigeon must never be a source of anything a player could not already get out
 * of that block by hand, because a mob that produces a NEW material is a route around whatever gates
 * that material - and this one wanders into your base on its own.
 *
 * <p>So it does not have a loot table. It rolls <b>the block's own pull table</b>, which makes the
 * subset structural rather than a thing two JSON files have to agree about. A bespoke table got this
 * wrong twice in one afternoon: first with wheat seeds, which come from the Hydroponics Bay's seedling
 * lottery and so walked past a Pump, water and power; then with bread, an apple and a feather, none of
 * which this world produces at all. Both readings looked reasonable while the rule was in someone's
 * head instead of in the code.
 *
 * <p>What stops that being "the bird sorts for you" is volume, not distribution: one peck per visit
 * against a tarp that empties a block in seconds.
 *
 * <p><b>On the pigeon staying ambiance rather than becoming a resource.</b> Anything that produces an
 * item on a timer is a resource if you leave it running long enough, and this mob was decided not to be
 * one. What keeps it honest is not the design, it is the numbers: a long cooldown between attempts, a
 * low chance on each, and a table of things nobody farms. The rate is config so it can be turned to
 * zero, and the tuning note is in {@code RCConfig} rather than here.
 *
 * <p>Built on vanilla's {@link MoveToBlockGoal} rather than hand-rolled pathing, so the walk-to-it half
 * behaves exactly like a fox going to a berry bush.
 */
public class PigeonForageGoal extends MoveToBlockGoal {

    private static final int PECK_TICKS = 40;

    private final PigeonEntity pigeon;
    private int cooldown;
    private int pecking;
    private boolean pecked;

    public PigeonForageGoal(PigeonEntity pigeon) {
        super(pigeon, 1.0, 8, 3);
        this.pigeon = pigeon;
        // Staggered so a flock that spawned together does not peck in unison on its first attempt.
        this.cooldown = pigeon.getRandom().nextInt(RCConfig.PIGEON_FORAGE_INTERVAL_TICKS.get());
    }

    @Override
    public boolean canUse() {
        if (!RCConfig.PIGEON_FORAGE_ENABLED.get()) {
            return false;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !pecked && RCConfig.PIGEON_FORAGE_ENABLED.get() && super.canContinueToUse();
    }

    /**
     * Household garbage only, by tag.
     *
     * <p>This asked {@code instanceof SortableBlock} once and it was wrong: Stone Rubble and Mechanical
     * Waste are sortable too, so pigeons foraged in broken concrete out in the demolition yard. See
     * {@link RCTags#PIGEON_FORAGEABLE}.
     */
    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(RCTags.PIGEON_FORAGEABLE);
    }

    /**
     * The table a peck at this block rolls: the block's own pull stream, and nothing else.
     *
     * <p>Exposed so a test can hold the gating rule to the wiring rather than to a comment. The whole
     * point is that there is no second table to drift.
     */
    public static @org.jspecify.annotations.Nullable ResourceKey<LootTable> tableFor(
            net.minecraft.world.level.block.Block block) {
        return SortableBlock.pullTableOf(block);
    }

    @Override
    public void start() {
        super.start();
        pecking = 0;
        pecked = false;
    }

    @Override
    public void stop() {
        super.stop();
        pecking = 0;
        cooldown = RCConfig.PIGEON_FORAGE_INTERVAL_TICKS.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (!isReachedTarget()) {
            return;
        }
        pigeon.getLookControl().setLookAt(Vec3.atCenterOf(blockPos));
        if (++pecking < PECK_TICKS) {
            return;
        }
        pecking = 0;
        // Set BEFORE the roll, so a peck that finds nothing still ends the visit. Otherwise a bird
        // stands there retrying until it succeeds, which is the same heap of loot arriving slightly
        // later.
        pecked = true;
        if (pigeon.level() instanceof ServerLevel level) {
            forage(level);
        }
        cooldown = RCConfig.PIGEON_FORAGE_INTERVAL_TICKS.get();
    }

    /**
     * One peck resolved. Most of them turn up nothing, which is the point of a pigeon.
     *
     * <p>Popped at the pile rather than given to the bird, so it is something you notice happening and
     * can walk over to, rather than an inventory the mob carries around.
     */
    private void forage(ServerLevel level) {
        level.playSound(null, blockPos, SoundEvents.CHICKEN_STEP.value(), SoundSource.NEUTRAL,
            0.5F, 1.6F);
        if (level.getRandom().nextFloat() >= RCConfig.PIGEON_FORAGE_CHANCE.get()) {
            return;
        }
        ResourceKey<LootTable> key = tableFor(level.getBlockState(blockPos).getBlock());
        if (key == null) {
            return;   // the pile went away, or something untagged crept in
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockPos))
            .create(LootContextParamSets.CHEST);
        List<ItemStack> found = table.getRandomItems(params);
        for (ItemStack stack : found) {
            if (!stack.isEmpty()) {
                Block.popResource(level, blockPos.above(), stack);
            }
        }
    }
}
