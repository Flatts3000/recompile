package com.flatts.recompile.gametest;

import com.flatts.recompile.content.worldgen.sewer.SewerPieces;
import com.flatts.recompile.content.worldgen.sewer.SewerStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * The pieces of a sewer, built the way {@code SewerStructure} builds them, for the tests that measure it.
 *
 * <p>One home for the fixtures rather than three, because {@link #layout} has already been caught out
 * twice by drifting from the real build order - once when access chambers landed and once when the sump
 * did - and three copies would be three chances to drift again. It lives beside {@link LootSearch} and
 * {@link RecipeResults} for the same reason those do.
 */
final class SewerFixtures {

    private SewerFixtures() {
    }

    static final int SEEDS = 200;

    /**
     * The smallest chamber {@code SewerRoom} can roll, at a given corner.
     *
     * <p>Smallest because it is the tightest case for everything hung off it - the dens sit at the high
     * ends of its walls and the corridors leave from its low corner, so a bigger chamber only ever moves
     * them further apart. Tests that want a den box ask this for a chamber and then ask
     * {@code SewerStructure} for the den, rather than retyping either shape.
     */
    private static BoundingBox smallestChamber(BlockPos corner) {
        return new BoundingBox(corner.getX(), corner.getY(), corner.getZ(),
            corner.getX() + 9, corner.getY() + 7, corner.getZ() + 9);
    }

    /**
     * One of {@code SewerStructure}'s den boxes, rebuilt at a corner of the caller's choosing.
     *
     * <p>The shape is the shipped one and the position is not, which is the split a habitability test
     * wants: it is asking whether three turtles fit in a room that size, and where the room sits relative
     * to the chamber is a different test's question ({@code the_dens_land_on_no_corridor}).
     */
    static BoundingBox shapedLike(
            java.util.function.Function<BoundingBox, BoundingBox> den, BlockPos corner) {
        BoundingBox shape = den.apply(smallestChamber(BlockPos.ZERO));
        return new BoundingBox(corner.getX(), corner.getY(), corner.getZ(),
            corner.getX() + shape.getXSpan() - 1,
            corner.getY() + shape.getYSpan() - 1,
            corner.getZ() + shape.getZSpan() - 1);
    }

    /** Build one sewer's piece tree, exactly as {@code SewerStructure} does, and hand back the pieces. */
    static List<StructurePiece> layout(long seed) {
        RandomSource random = RandomSource.create(seed);
        StructurePiecesBuilder builder = new StructurePiecesBuilder();
        SewerPieces.SewerRoom room = new SewerPieces.SewerRoom(0, random, 0, 0);
        builder.addPiece(room);
        room.addChildren(room, builder, random);
        // MIRROR THE STRUCTURE EXACTLY, in its order. A layout built without these steps measures a
        // sewer nobody will ever generate - which has now caught this helper out twice, once for the
        // access chamber and once for the sump.
        SewerPieces.attachSump(room, builder, random);
        SewerPieces.forceAccessChamber(room, builder, random);
        List<StructurePiece> pieces = new ArrayList<>();
        builder.build().pieces().forEach(pieces::add);
        return pieces;
    }
}
