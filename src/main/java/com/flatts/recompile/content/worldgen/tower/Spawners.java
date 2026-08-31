package com.flatts.recompile.content.worldgen.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

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
 * else public that can carry equipment. {@code load} takes the whole spawner tag, so it is the only
 * route that can hand the mob a hat.
 */
final class Spawners {

    private Spawners() {
    }

    /**
     * Seal a spawner into the world at {@code pos}.
     *
     * @param sunCap whether the mob needs a hat to survive daylight - see the two call sites, because
     *               the answer is not the same for both and is not what it looks like
     */
    static void place(WorldGenLevel level, BoundingBox limit, BlockPos pos, String entityId, boolean sunCap) {
        if (!limit.isInside(pos)) {
            return;
        }
        level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SpawnerBlockEntity spawner)) {
            return;
        }

        CompoundTag entity = new CompoundTag();
        entity.putString("id", entityId);
        if (sunCap) {
            CompoundTag hat = new CompoundTag();
            hat.putString("id", "minecraft:leather_helmet");
            hat.putInt("count", 1);
            CompoundTag equipment = new CompoundTag();
            equipment.put("head", hat);
            entity.put("equipment", equipment);
        }

        CompoundTag spawnData = new CompoundTag();
        spawnData.put("entity", entity);
        CompoundTag root = new CompoundTag();
        root.put("SpawnData", spawnData);

        // NULL LEVEL, AND THIS DEADLOCKS THE SERVER THREAD IF YOU PASS ONE.
        //
        // The parameter is @Nullable and it has to be null here. SpawnerBlockEntity overrides
        // setNextSpawnData to notify the level, and the only Level a WorldGenLevel can hand back is the
        // ServerLevel - so asking it about a chunk that is at that moment mid-generation waits for a
        // generation that is waiting on this call. The symptom is not an exception: the server thread
        // simply stops, devbridge still answers ping because that is off-thread, and every command
        // times out with nothing in the log.
        //
        // Vanilla's own structures pass null for the same reason.
        spawner.getSpawner().load(null, pos,
            TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), root));
        blockEntity.setChanged();
    }
}
