package com.flatts.recompile.content.worldgen.sewer;

import com.flatts.recompile.registry.RCStructures;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * The sewer (#90, {@code docs/sewers_spec.md} phase 2): the mod's mineshaft, in brick, running leachate.
 *
 * <p>The piece graph lives in {@link SewerPieces}; this class does the two things a {@link Structure}
 * is for - decide where a sewer starts, and hand back the tree.
 *
 * <p><b>Build at a fixed height, then move the whole thing.</b> The room is rooted at y=50 and the
 * corridors grow from it, and only afterwards is the entire piece tree shifted to where it belongs.
 * That is vanilla's own trick and it is what lets every piece do its geometry in flat local coordinates
 * without any of them knowing the terrain. Ours differs from the mineshaft's in one way that matters:
 * the mineshaft calls {@code moveBelowSeaLevel}, and <b>this world's sea level is -64</b>, so that
 * would drop a sewer straight through the floor and into the void. The depth is chosen against the
 * rock instead.
 */
public class SewerStructure extends Structure {

    public static final MapCodec<SewerStructure> CODEC = simpleCodec(SewerStructure::new);

    /**
     * Where the top of a sewer sits, measured down from the surface.
     *
     * <p>Six blocks of cover under the coarse-dirt cap: enough that a corridor ceiling never breaks
     * daylight, and shallow enough that the whole descent still fits. The terrain is 59-63 thick with
     * 55-61 of that tunnelable ({@code the_world_has_rock_enough_to_hold_a_sewer}), and a worst-case
     * descent is about 40, so starting six under the floor leaves real margin above the bedrock.
     */
    private static final int COVER = 6;

    /** The lowest a sewer may reach. Bedrock sits at about y=5, so this keeps a floor beneath it. */
    private static final int FLOOR_LIMIT = 12;

    /** Where the pieces are built before being moved. Matches vanilla's own magic start height. */
    private static final int BUILD_Y = 50;

    public SewerStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        StructurePiecesBuilder pieces = new StructurePiecesBuilder();
        int shift = buildAndSink(pieces, context);
        return Optional.of(new Structure.GenerationStub(
            new BlockPos(chunk.getMiddleBlockX(), BUILD_Y + shift, chunk.getMinBlockZ()),
            Either.right(pieces)));
    }

    /**
     * Build the tree at {@link #BUILD_Y}, then drop it under the surface and return how far it moved.
     *
     * <p>The sink is computed from the <b>assembled</b> tree rather than from the room alone, because
     * corridors drift downward as they branch: a sewer whose room clears the floor can still have a
     * stairwell hanging in the void 40 blocks below it. Measuring the finished bounding box and
     * clamping on its lowest point is what makes "it never opens into the void" true of the whole
     * structure rather than of its first piece.
     */
    private static int buildAndSink(StructurePiecesBuilder pieces, Structure.GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        RandomSource random = context.random();
        SewerPieces.SewerRoom room = new SewerPieces.SewerRoom(
            0, random, chunk.getBlockX(2), chunk.getBlockZ(2));
        pieces.addPiece(room);
        room.addChildren(room, pieces, random);

        BlockPos centre = pieces.getBoundingBox().getCenter();
        int surface = context.chunkGenerator().getBaseHeight(
            centre.getX(), centre.getZ(),
            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
            context.heightAccessor(), context.randomState());

        // Put the TOP of the structure COVER blocks under the surface...
        int wanted = surface - COVER - pieces.getBoundingBox().maxY();
        // ...unless that would push its lowest point through the floor, in which case sit on the floor
        // and accept thinner cover. Both ends are clamped because a sewer that breaks the surface and a
        // sewer that opens into the void are the same bug seen from opposite sides.
        int lowest = pieces.getBoundingBox().minY() + wanted;
        if (lowest < FLOOR_LIMIT) {
            wanted += FLOOR_LIMIT - lowest;
        }
        pieces.offsetPiecesVertically(wanted);
        return wanted;
    }

    @Override
    public StructureType<?> type() {
        return RCStructures.SEWER.get();
    }
}
