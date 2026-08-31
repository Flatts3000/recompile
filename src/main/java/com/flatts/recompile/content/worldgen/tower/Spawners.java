package com.flatts.recompile.content.worldgen.tower;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;

/**
 * One vanilla spawner in each landmark (owner, 2026-08-31).
 *
 * <p><b>This reverses "landmarks pay nothing", and the reversal is the owner's.</b> #307 and #308 both
 * ruled that these structures hold nothing, on the grounds that a landmark is arrived at rather than
 * cleared. A spawner is not loot, but it is not nothing either - it is a hazard, and left alone it is a
 * farm. Recorded here rather than left for someone to find as a contradiction.
 *
 * <p><b>Why {@code load} and not {@code setEntityId}.</b> {@link net.minecraft.world.level.BaseSpawner}
 * exposes {@code setEntityId}, which writes an id into a spawn-data tag it keeps to itself, and nothing
 * else public that can carry a spawn range or equipment. {@code load} takes the whole spawner tag, so
 * it is the only route that can configure one.
 */
final class Spawners {

    private static final Logger LOG = LogUtils.getLogger();

    private Spawners() {
    }

    /**
     * Seal a spawner into the world at {@code pos}.
     *
     * @param spawnRange how far from the spawner a mob may appear. The candidate position is the
     *                   spawner's own plus {@code (nextDouble() - nextDouble()) * range} per axis, so
     *                   inside a chimney whose flue is about two and a half blocks across, a range of
     *                   4 puts most successful spawns in the open air outside the brick.
     *                   <p><b>That is wanted rather than avoided</b> (owner, 2026-08-31): walking past
     *                   a smokestack should put mobs around you. An earlier version clamped this to 1
     *                   to keep them sealed in the flue until somebody broke in, and that was the
     *                   wrong call - it made the structure inert to anyone who did not attack it.
     * @param equipment  a loot table for what the mob wears, or null.
     *                   <p><b>This goes in SpawnData rather than in the entity tag, and the difference
     *                   is a bow.</b> {@code BaseSpawner.serverTick} treats a spawn as unconfigured
     *                   only when the entity tag holds exactly one key, and NeoForge calls
     *                   {@code finalizeSpawn} only for unconfigured spawns. Writing equipment into the
     *                   entity tag takes it to two keys, which silently skips finalization - and
     *                   {@code AbstractSkeleton.finalizeSpawn} is what hands a skeleton its bow and
     *                   picks its ranged goal. A Parched configured that way spawns empty-handed and
     *                   punches. The SpawnData field is applied after finalization instead, so it gets
     *                   both.
     *                   <p><b>The table must not touch the item's equippable component.</b> A leather
     *                   helmet already carries a complete one, and Mob.equip reads it to decide which
     *                   slot the roll goes in. Writing a partial {@code equippable} through
     *                   {@code set_components} - even one naming the right slot - replaces the real
     *                   component, the slot lookup stops recognising it as headgear, and the cap ends
     *                   up carried rather than worn. The mob then burns with the hat in its inventory,
     *                   which is what shipped. Vanilla's own equipment tables only ever set trim.
     */
    static void place(WorldGenLevel level, BoundingBox limit, BlockPos pos, String entityId,
            int spawnRange, String equipment) {
        if (!limit.isInside(pos)) {
            return;
        }
        level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SpawnerBlockEntity spawner)) {
            // Leaving a placed but unconfigured spawner would be worse than leaving the column alone:
            // it looks like content and spawns nothing forever.
            LOG.warn("no spawner block entity at {}; leaving the column empty rather than a dead spawner",
                pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            return;
        }

        CompoundTag entity = new CompoundTag();
        entity.putString("id", entityId);

        CompoundTag spawnData = new CompoundTag();
        spawnData.put("entity", entity);

        // SPAWN AT ANY LIGHT LEVEL (owner, 2026-08-31), and this is not the freebie it looks like.
        // A plain spawner is NOT exempt from the darkness check: EntitySpawnReason
        // .ignoresLightRequirements is true only for TRIAL_SPAWNER, and the isSpawner carve-out in
        // Monster.checkSurfaceMonstersSpawnRules waives sky VISIBILITY, not light. So without this both
        // of these would sit dead through every day and only work at night, which is not what a
        // landmark you walk up to in the afternoon should do.
        //
        // An empty custom_spawn_rules is the whole fix: both limits default to the full 0..15 range.
        // Its presence is what matters - BaseSpawner uses it INSTEAD of
        // SpawnPlacements.checkSpawnRules, so the light test is gone rather than widened. Note that
        // takes the ground check with it, and noCollision is all that remains; a mob can appear in the
        // air and drop, which in a dump is fine.
        spawnData.put("custom_spawn_rules", new CompoundTag());
        if (equipment != null) {
            CompoundTag chances = new CompoundTag();
            chances.putFloat("head", 0.0F);
            CompoundTag table = new CompoundTag();
            table.putString("loot_table", equipment);
            table.put("slot_drop_chances", chances);
            spawnData.put("equipment", table);
        }

        CompoundTag root = new CompoundTag();
        root.put("SpawnData", spawnData);
        root.putShort("SpawnRange", (short) spawnRange);

        // A LOGGING REPORTER, NOT ProblemReporter.DISCARDING. BaseSpawner.load only sets the spawn data
        // if the codec read succeeds, so a malformed tag - a renamed key, a typo in a slot name from
        // some later edit - leaves a spawner that spawns nothing, in a structure, with not one line
        // anywhere saying so. This repo has paid for that class of silence often enough.
        try (ProblemReporter.ScopedCollector problems = new ProblemReporter.ScopedCollector(LOG)) {
            // NULL LEVEL, AND THIS DEADLOCKS THE SERVER THREAD IF YOU PASS ONE. The parameter is
            // @Nullable and has to be null here: SpawnerBlockEntity overrides setNextSpawnData to
            // notify the level, and the only Level a WorldGenLevel can hand back is the ServerLevel -
            // so asking it about a chunk that is at that moment mid-generation waits on a generation
            // waiting on this call. There is no exception and nothing in the log; the thread simply
            // stops. Vanilla's own structures pass null for the same reason.
            spawner.getSpawner().load(null, pos,
                TagValueInput.create(problems, level.registryAccess(), root));
        }
        blockEntity.setChanged();
    }
}
