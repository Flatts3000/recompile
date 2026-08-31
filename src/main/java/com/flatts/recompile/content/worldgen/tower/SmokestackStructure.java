package com.flatts.recompile.content.worldgen.tower;

import com.flatts.recompile.registry.RCStructures;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The demolition yard's skyline (#308).
 *
 * <p>Unlike the cooling tower, which stops working the moment you can see two at once, chimneys came
 * in groups - so this places several across a region rather than one every few thousand blocks. That
 * is the whole difference between the two structure sets.
 */
public class SmokestackStructure extends Structure {

    public static final MapCodec<SmokestackStructure> CODEC = simpleCodec(SmokestackStructure::new);

    public SmokestackStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> {
            int x = context.chunkPos().getBlockX(8);
            int z = context.chunkPos().getBlockZ(8);
            int base = context.chunkGenerator().getFirstOccupiedHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            builder.addPiece(new SmokestackPiece(context.random(), x, base, z));
        });
    }

    @Override
    public StructureType<?> type() {
        return RCStructures.SMOKESTACK.get();
    }
}
