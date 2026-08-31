package com.flatts.recompile.content.worldgen.tower;

import com.flatts.recompile.registry.RCStructures;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The cooling tower (#307): the first thing in this world you can see from another region.
 *
 * <p><b>It is a landmark and it pays nothing.</b> There is no loot, no chest and no interior room -
 * the reward is arriving. That looks like it breaks the sewer's rule that finite content needs a
 * reason to clear it, and it does not: a landmark is never <em>cleared</em>, it is walked to, so the
 * rule does not reach it. What it gives is a destination and evidence that somebody was here, and a
 * chest in the bottom would turn it back into a dungeon with a good silhouette.
 *
 * <p><b>It generates in {@code top_layer_modification}, not {@code surface_structures}, and that is
 * load-bearing.</b> {@code applyBiomeDecoration} runs each decoration step in order, placing that
 * step's structures before its features, and this biome's {@code radioactive_scatter} sits at
 * {@code vegetal_decoration} - five steps later. Writing the shell first raises
 * {@code WORLD_SURFACE_WG}, which is the heightmap the scatter places against, so the tailings
 * feature would site an impoundment on the rim seventy blocks up. Its own ground search only looks
 * eight blocks down, so it would build the slab and its pond in mid-air rather than give up - which is
 * exactly the floating-ring failure that feature's comments already warn about. An origin landing
 * inside the cleared interior would have put Mill Tailings in the middle of a landmark whose whole
 * point is that it pays nothing.
 *
 * <p>Running last instead means the scatter samples the ground before the tower exists, and the shell
 * then overwrites whatever it placed. Same result for every other feature the tower cuts through.
 *
 * <p><b>Why a structure and not a feature.</b> {@code ChunkStatus.FEATURES} carries
 * {@code blockStateWriteRadius(1)}, so a feature may only write 16 blocks from its origin in the worst
 * case - the same limit that capped the tailings impoundment at radius 12. This is over thirty across
 * and seventy tall, so it goes through the structure path, where a piece is called once per chunk it
 * touches and clips its own writes.
 */
public class CoolingTowerStructure extends Structure {

    public static final MapCodec<CoolingTowerStructure> CODEC = simpleCodec(CoolingTowerStructure::new);

    public CoolingTowerStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        // WORLD_SURFACE_WG, not OCEAN_FLOOR_WG: the tower stands on whatever the generator put down,
        // and this world's surface is a flat coarse-dirt cap, so the two agree. Naming the one that
        // means "the top" keeps it honest if the terrain ever gains something above the cap.
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> {
            RandomSource random = context.random();
            int x = context.chunkPos().getBlockX(8);
            int z = context.chunkPos().getBlockZ(8);
            int base = context.chunkGenerator().getFirstOccupiedHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            builder.addPiece(new CoolingTowerPiece(random, x, base, z));
        });
    }

    @Override
    public StructureType<?> type() {
        return RCStructures.COOLING_TOWER.get();
    }
}
