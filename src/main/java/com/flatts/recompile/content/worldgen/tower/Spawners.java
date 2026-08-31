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
     * @param spawnRange how far from the spawner a mob may appear. <b>Not optional, and the default is
     *                   wrong for anything enclosed.</b> {@code BaseSpawner} defaults to 4, and the
     *                   candidate position is the spawner's own plus
     *                   {@code (nextDouble() - nextDouble()) * range} per axis - so inside a chimney
     *                   whose flue is about two and a half blocks across, most successful spawns land
     *                   in the open air outside the brick. A structure that is supposed to do nothing
     *                   until it is broken into instead drips mobs onto the yard.
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
