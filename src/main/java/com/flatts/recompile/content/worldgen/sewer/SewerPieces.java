package com.flatts.recompile.content.worldgen.sewer;

import com.flatts.recompile.registry.RCStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.jspecify.annotations.Nullable;

/**
 * The sewer's pieces and the graph that lays them out (#90, {@code docs/sewers_spec.md} phase 2).
 *
 * <p><b>This mirrors vanilla's mineshaft and shares no code with it, on purpose.</b> The reuse boundary
 * was measured rather than guessed: {@link StructurePiece} is public API and supplies the whole toolbox
 * this file leans on - {@code generateBox}, {@code placeBlock}, {@code getWorldPos}, the bounding-box
 * math, mirror and rotation, NBT round-trip - but the mineshaft's own pieces cannot be extended.
 * {@code MineshaftPieces.MineShaftPiece} is package-private, so the shared base is unreachable, and
 * {@code createRandomShaftPiece} is private and hardcodes {@code new MineShaftCorridor(...)}, so the
 * recursion is closed: a subclassed corridor's {@code addChildren} would still build vanilla oak pieces.
 * An access transformer widens visibility but cannot change what a private factory constructs. The
 * separate and independent reason not to copy it is that decompiled Mojang code is not licensed for
 * redistribution - the same split already recorded for Create (MIT code, all-rights-reserved assets).
 *
 * <p><b>The graph is the part worth mirroring</b>, and it is small: a corridor continues straight half
 * the time and turns left or right otherwise, drifting up to a block vertically at each link, with
 * crossings at 20% and stairs at 10%. Recursion stops at {@link #MAX_DEPTH}, and nothing generates
 * further than {@link #RADIUS_CAP} from the root, which is what keeps a sewer finite and stops two of
 * them merging.
 */
public final class SewerPieces {

    /** How many links deep the recursion goes. Vanilla's mineshaft uses 8 and it sprawls plenty. */
    public static final int MAX_DEPTH = 8;

    /**
     * How far from the root a piece may be placed, in blocks on each horizontal axis.
     *
     * <p>This is what "it is bounded - two sewers do not merge, and one does not run for a thousand
     * blocks" means in code. With {@link #MAX_DEPTH} links of at most 8 blocks each the reach is already
     * limited, but the cap is what makes it a guarantee rather than an emergent property of two other
     * numbers that somebody may later retune.
     */
    public static final int RADIUS_CAP = 80;

    /** Interior width and height of a corridor: three across, three tall, plus a floor. */
    private static final int BORE = 3;

    /** How long one corridor segment runs before the next piece decides what happens. */
    private static final int SEGMENT = 5;

    /** How far a stairs piece descends, and how long it is. Vanilla drops 5 over 8; so do we. */
    private static final int STAIR_DROP = 5;
    private static final int STAIR_RUN = 8;

    private SewerPieces() {
    }

    // ---------------------------------------------------------------- the graph

    /**
     * Pick a piece for the given opening, or null if nothing fits there.
     *
     * <p>Vanilla's split, kept: 20% crossing, 10% stairs, 70% corridor. Those proportions are what make
     * a mineshaft read as a mineshaft - mostly tunnel, occasional junction, the rare descent - and there
     * is no reason a sewer should read differently.
     */
    private static @Nullable SewerPiece choose(StructurePieceAccessor pieces, RandomSource random,
            int x, int y, int z, @Nullable Direction facing, int depth) {
        int roll = random.nextInt(100);
        if (roll >= 80) {
            BoundingBox box = boxFor(x, y, z, facing, BORE, BORE + 1, BORE);
            return pieces.findCollisionPiece(box) == null
                ? new SewerCrossing(depth, box, facing) : null;
        }
        if (roll >= 70) {
            BoundingBox box = stairBox(x, y, z, facing);
            return pieces.findCollisionPiece(box) == null
                ? new SewerStairs(depth, box, facing) : null;
        }
        BoundingBox box = boxFor(x, y, z, facing, BORE, BORE + 1, SEGMENT);
        return pieces.findCollisionPiece(box) == null
            ? new SewerCorridor(depth, box, facing) : null;
    }

    /**
     * Add one piece and recurse into it, unless we are too deep or too far out.
     *
     * <p>Both guards live here rather than inside the pieces, so there is exactly one place a sewer's
     * extent is decided and a new piece type cannot forget to check them.
     */
    static @Nullable SewerPiece grow(StructurePiece root, StructurePieceAccessor pieces,
            RandomSource random, int x, int y, int z, Direction facing, int depth) {
        if (depth > MAX_DEPTH) {
            return null;
        }
        if (Math.abs(x - root.getBoundingBox().minX()) > RADIUS_CAP
                || Math.abs(z - root.getBoundingBox().minZ()) > RADIUS_CAP) {
            return null;
        }
        SewerPiece piece = choose(pieces, random, x, y, z, facing, depth + 1);
        if (piece != null) {
            pieces.addPiece(piece);
            piece.addChildren(root, pieces, random);
        }
        return piece;
    }

    /** A box of the given bore and length, laid out along {@code facing} from the opening. */
    private static BoundingBox boxFor(int x, int y, int z, @Nullable Direction facing,
            int width, int height, int length) {
        int w = width - 1;
        int h = height - 1;
        int l = length - 1;
        return switch (facing == null ? Direction.NORTH : facing) {
            case SOUTH -> new BoundingBox(x, y, z, x + w, y + h, z + l);
            case WEST -> new BoundingBox(x - l, y, z, x, y + h, z + w);
            case EAST -> new BoundingBox(x, y, z, x + l, y + h, z + w);
            default -> new BoundingBox(x, y, z - l, x + w, y + h, z);
        };
    }

    /** A stairs box: same bore, but it reaches {@link #STAIR_DROP} blocks below the opening. */
    private static BoundingBox stairBox(int x, int y, int z, @Nullable Direction facing) {
        BoundingBox flat = boxFor(x, y, z, facing, BORE, BORE + 1, STAIR_RUN);
        return new BoundingBox(flat.minX(), flat.minY() - STAIR_DROP, flat.minZ(),
            flat.maxX(), flat.maxY(), flat.maxZ());
    }

    // ---------------------------------------------------------------- the pieces

    /** What every sewer piece shares: its type and its box, and nothing else. Deliberately thin. */
    public abstract static class SewerPiece extends StructurePiece {

        protected SewerPiece(StructurePieceType type, int depth, BoundingBox box) {
            super(type, depth, box);
        }

        protected SewerPiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }

        /**
         * Nothing to save. The box, the depth and the orientation are written by {@link StructurePiece}
         * itself, and a sewer piece has no state of its own - the same instinct as the multiblock
         * framework's "no BlockEntity for the structure".
         */
        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        }

        /**
         * Hollow the box out, shell it in brick, and optionally run a channel down the middle.
         *
         * <p>One method for every piece, because a crossing is a wider corridor and a room is a wider
         * crossing - the differences are dimensions, not construction. Shared so the fluid cannot end
         * up one block deep in a corridor and two in a room by accident, which is the whole content of
         * the depth decision.
         */
        protected void carve(WorldGenLevel level, BoundingBox limit, boolean channel) {
            BoundingBox box = this.boundingBox;
            int x1 = box.maxX() - box.minX();
            int y1 = box.maxY() - box.minY();
            int z1 = box.maxZ() - box.minZ();
            // Shell then hollow in one call: generateBox's two-state form paints the outer layer with
            // the first state and the interior with the second, which is exactly a lined tunnel.
            this.generateBox(level, limit, 0, 0, 0, x1, y1, z1,
                SewerPalette.WALL, SewerPalette.HOLLOW, false);
            if (!channel) {
                return;
            }
            // The channel is recessed into the floor, so the fluid sits below the walkway rather than
            // on it. That keeps a corridor walkable while still reading as a sewer, and it is why the
            // fluid cannot spill: it has a brick lip on both sides.
            int midX = x1 / 2;
            int midZ = z1 / 2;
            boolean alongZ = z1 >= x1;
            int span = alongZ ? z1 : x1;
            for (int i = 1; i < span; i++) {
                int cx = alongZ ? midX : i;
                int cz = alongZ ? i : midZ;
                this.placeBlock(level, SewerPalette.FLUID, cx, 1, cz, limit);
            }
        }

        /** Sparse cobwebs on the ceiling: the mineshaft parallel, and the game's only source of them. */
        protected void cobwebs(WorldGenLevel level, BoundingBox limit, RandomSource random) {
            BoundingBox box = this.boundingBox;
            int x1 = box.maxX() - box.minX();
            int y1 = box.maxY() - box.minY();
            int z1 = box.maxZ() - box.minZ();
            for (int x = 1; x < x1; x++) {
                for (int z = 1; z < z1; z++) {
                    if (random.nextInt(60) == 0) {
                        this.placeBlock(level, SewerPalette.WEB, x, y1 - 1, z, limit);
                    }
                }
            }
        }
    }

    /** A straight run of tunnel, and the piece that does the branching. */
    public static class SewerCorridor extends SewerPiece {

        public SewerCorridor(int depth, BoundingBox box, @Nullable Direction facing) {
            super(RCStructures.SEWER_CORRIDOR.get(), depth, box);
            this.setOrientation(facing);
        }

        public SewerCorridor(CompoundTag tag) {
            super(RCStructures.SEWER_CORRIDOR.get(), tag);
        }

        /**
         * Straight half the time, left a quarter, right a quarter, drifting up to a block vertically.
         *
         * <p>The vertical drift is what gives a sewer levels without every descent needing a stairs
         * piece, and it is the cheapest way to get one: an offset on the next opening, no extra piece
         * type and no extra geometry.
         */
        @Override
        public void addChildren(StructurePiece root, StructurePieceAccessor pieces, RandomSource random) {
            Direction facing = this.getOrientation();
            if (facing == null) {
                return;
            }
            int depth = this.getGenDepth();
            int y = this.boundingBox.minY() + random.nextInt(3) - 1;
            BoundingBox box = this.boundingBox;
            int roll = random.nextInt(4);
            Direction next = roll <= 1 ? facing
                : roll == 2 ? facing.getCounterClockWise() : facing.getClockWise();
            switch (next) {
                case NORTH -> grow(root, pieces, random, box.minX(), y, box.minZ() - 1, next, depth);
                case SOUTH -> grow(root, pieces, random, box.minX(), y, box.maxZ() + 1, next, depth);
                case WEST -> grow(root, pieces, random, box.minX() - 1, y, box.minZ(), next, depth);
                default -> grow(root, pieces, random, box.maxX() + 1, y, box.minZ(), next, depth);
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
            this.carve(level, limit, true);
            this.cobwebs(level, limit, random);
        }
    }

    /** A junction: same bore, square, and it opens every way except back. */
    public static class SewerCrossing extends SewerPiece {

        public SewerCrossing(int depth, BoundingBox box, @Nullable Direction facing) {
            super(RCStructures.SEWER_CROSSING.get(), depth, box);
            this.setOrientation(facing);
        }

        public SewerCrossing(CompoundTag tag) {
            super(RCStructures.SEWER_CROSSING.get(), tag);
        }

        @Override
        public void addChildren(StructurePiece root, StructurePieceAccessor pieces, RandomSource random) {
            Direction facing = this.getOrientation();
            if (facing == null) {
                return;
            }
            int depth = this.getGenDepth();
            BoundingBox box = this.boundingBox;
            // Every direction except back the way we came, which is where the parent already is.
            for (Direction out : Direction.Plane.HORIZONTAL) {
                if (out == facing.getOpposite()) {
                    continue;
                }
                switch (out) {
                    case NORTH -> grow(root, pieces, random, box.minX(), box.minY(), box.minZ() - 1, out, depth);
                    case SOUTH -> grow(root, pieces, random, box.minX(), box.minY(), box.maxZ() + 1, out, depth);
                    case WEST -> grow(root, pieces, random, box.minX() - 1, box.minY(), box.minZ(), out, depth);
                    default -> grow(root, pieces, random, box.maxX() + 1, box.minY(), box.minZ(), out, depth);
                }
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
            this.carve(level, limit, true);
            // A grate in the ceiling, which is what a sewer junction has and what stops a crossing
            // reading as nothing more than a wide bit of corridor.
            BoundingBox box = this.boundingBox;
            this.placeBlock(level, SewerPalette.GRATE,
                (box.maxX() - box.minX()) / 2, box.maxY() - box.minY(),
                (box.maxZ() - box.minZ()) / 2, limit);
        }
    }

    /** The descent: down five over eight, which is what turns drift into genuine levels. */
    public static class SewerStairs extends SewerPiece {

        public SewerStairs(int depth, BoundingBox box, @Nullable Direction facing) {
            super(RCStructures.SEWER_STAIRS.get(), depth, box);
            this.setOrientation(facing);
        }

        public SewerStairs(CompoundTag tag) {
            super(RCStructures.SEWER_STAIRS.get(), tag);
        }

        @Override
        public void addChildren(StructurePiece root, StructurePieceAccessor pieces, RandomSource random) {
            Direction facing = this.getOrientation();
            if (facing == null) {
                return;
            }
            int depth = this.getGenDepth();
            BoundingBox box = this.boundingBox;
            switch (facing) {
                case NORTH -> grow(root, pieces, random, box.minX(), box.minY(), box.minZ() - 1, facing, depth);
                case SOUTH -> grow(root, pieces, random, box.minX(), box.minY(), box.maxZ() + 1, facing, depth);
                case WEST -> grow(root, pieces, random, box.minX() - 1, box.minY(), box.minZ(), facing, depth);
                default -> grow(root, pieces, random, box.maxX() + 1, box.minY(), box.minZ(), facing, depth);
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
            // NO CHANNEL, deliberately. A slope is the one place the fluid would actually run, and a
            // stairwell filling with leachate is both a flood risk during generation and exactly the
            // head-height column that made canDrown a problem in the first place.
            this.carve(level, limit, false);
        }
    }

    /** The root chamber a sewer grows out of. Wider, taller, and it opens all four ways. */
    public static class SewerRoom extends SewerPiece {

        /**
         * Rooted at y=50 the way vanilla's mineshaft room is, then moved as a whole by
         * {@code SewerStructure} once the piece tree is built. Building at a fixed height and shifting
         * afterwards is vanilla's own trick and it is why no piece needs to know the terrain.
         */
        public SewerRoom(int depth, RandomSource random, int x, int z) {
            super(RCStructures.SEWER_ROOM.get(), depth, new BoundingBox(
                x, 50, z, x + 7 + random.nextInt(6), 54 + random.nextInt(4), z + 7 + random.nextInt(6)));
        }

        public SewerRoom(CompoundTag tag) {
            super(RCStructures.SEWER_ROOM.get(), tag);
        }

        @Override
        public void addChildren(StructurePiece root, StructurePieceAccessor pieces, RandomSource random) {
            int depth = this.getGenDepth();
            BoundingBox box = this.boundingBox;
            grow(root, pieces, random, box.minX(), box.minY(), box.minZ() - 1, Direction.NORTH, depth);
            grow(root, pieces, random, box.minX(), box.minY(), box.maxZ() + 1, Direction.SOUTH, depth);
            grow(root, pieces, random, box.minX() - 1, box.minY(), box.minZ(), Direction.WEST, depth);
            grow(root, pieces, random, box.maxX() + 1, box.minY(), box.minZ(), Direction.EAST, depth);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
            this.carve(level, limit, false);
            // A pool rather than a channel: the room is the one place the fluid is allowed to be wide.
            // Still exactly one block deep, and inset by two so it never touches the wall a corridor
            // might open through.
            BoundingBox box = this.boundingBox;
            int x1 = box.maxX() - box.minX();
            int z1 = box.maxZ() - box.minZ();
            for (int x = 2; x < x1 - 1; x++) {
                for (int z = 2; z < z1 - 1; z++) {
                    this.placeBlock(level, SewerPalette.FLUID, x, 1, z, limit);
                }
            }
            this.cobwebs(level, limit, random);
        }
    }
}
