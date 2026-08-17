package com.flatts.recompile.content.worldgen.sewer;

import com.flatts.recompile.registry.RCStructures;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.OptionalInt;
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
        RandomSource random = context.random();
        StructurePiecesBuilder pieces = new StructurePiecesBuilder();
        SewerPieces.SewerRoom room = new SewerPieces.SewerRoom(
            0, random, chunk.getBlockX(2), chunk.getBlockZ(2));
        pieces.addPiece(room);
        room.addChildren(room, pieces, random);

        // THE LOWEST SURFACE OVER THE WHOLE FOOTPRINT, not the height at the middle. Pieces reach 80
        // blocks out on each axis and the surface ranges 63..69 across this world, so a tree sunk
        // against a centre height of 69 puts its roof at 63 - exactly ground level anywhere the far end
        // dips that low. Same failure the clamp was written to stop, driven by horizontal variance
        // instead of by depth, and equally silent.
        var footprint = pieces.getBoundingBox();
        int surface = Integer.MAX_VALUE;
        for (int px : new int[]{footprint.minX(), footprint.getCenter().getX(), footprint.maxX()}) {
            for (int pz : new int[]{footprint.minZ(), footprint.getCenter().getZ(), footprint.maxZ()}) {
                surface = Math.min(surface, context.chunkGenerator().getBaseHeight(px, pz,
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState()));
            }
        }

        OptionalInt shift = sink(surface,
            pieces.getBoundingBox().minY(), pieces.getBoundingBox().maxY());
        if (shift.isEmpty()) {
            return Optional.empty();
        }
        pieces.offsetPiecesVertically(shift.getAsInt());
        return Optional.of(new Structure.GenerationStub(
            new BlockPos(chunk.getMiddleBlockX(), BUILD_Y + shift.getAsInt(), chunk.getMiddleBlockZ()),
            Either.right(pieces)));
    }

    /**
     * How far to drop an assembled piece tree, or empty if it does not fit in the rock at all.
     *
     * <p>Pure arithmetic, and separated out for exactly that reason: the two ways this goes wrong are
     * invisible in a world. A sewer in the void looks like a broken generator and a sewer breaking the
     * surface looks like a broken structure, and neither throws anything.
     *
     * <p>The sink is computed from the <b>assembled</b> tree rather than from the room, because
     * corridors drift downward as they branch - a sewer whose room clears the floor can still have a
     * stairwell hanging forty blocks below it.
     *
     * <p><b>Both ends are clamped, and when they conflict the sewer does not generate.</b> An earlier
     * version clamped only the floor, so a tree too deep for the rock was pushed bodily upward until it
     * broke daylight: {@code tree y=0..57} against a surface at 65 came out spanning 12..69, ten blocks
     * of corridor in the open air. Raising the floor and lowering the roof cannot both be satisfied by
     * a tree taller than the rock, and the honest answer there is no sewer here rather than a broken
     * one - {@code findGenerationPoint} returning empty is a supported outcome that simply skips this
     * attempt.
     */
    public static OptionalInt sink(int surface, int treeMinY, int treeMaxY) {
        int roofLimit = surface - COVER;
        // Put the TOP of the structure COVER blocks under the surface...
        int wanted = roofLimit - treeMaxY;
        // ...then raise it if that would leave its lowest point in the void.
        int lowest = treeMinY + wanted;
        if (lowest < FLOOR_LIMIT) {
            wanted += FLOOR_LIMIT - lowest;
        }
        // If raising it broke the roof, the tree is simply taller than the rock available.
        if (treeMaxY + wanted > roofLimit) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(wanted);
    }

    @Override
    public StructureType<?> type() {
        return RCStructures.SEWER.get();
    }
}
