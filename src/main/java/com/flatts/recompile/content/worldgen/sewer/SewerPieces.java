package com.flatts.recompile.content.worldgen.sewer;

import com.flatts.recompile.registry.RCStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
 * this file leans on - {@code generateBox}, {@code placeBlock}, {@code makeBoundingBox}, the
 * bounding-box math, NBT round-trip - but the mineshaft's own pieces cannot be extended.
 * {@code MineshaftPieces.MineShaftPiece} is package-private, so the shared base is unreachable, and
 * {@code createRandomShaftPiece} is private and hardcodes {@code new MineShaftCorridor(...)}, so the
 * recursion is closed: a subclassed corridor's {@code addChildren} would still build vanilla oak pieces.
 * An access transformer widens visibility but cannot change what a private factory constructs. The
 * separate and independent reason not to copy it is that decompiled Mojang code is not licensed for
 * redistribution - the same split already recorded for Create (MIT code, all-rights-reserved assets).
 *
 * <p><b>Local coordinates are not world coordinates, and that cost this file a rewrite.</b>
 * {@code StructurePiece.getWorldX(x, z)} returns {@code minX + z} for an EAST piece and {@code maxX - z}
 * for WEST - local X and local Z <em>swap</em> on the X axis - and when {@code getOrientation()} is null
 * it hands back the local value untouched, as an absolute coordinate. The first version derived its
 * extents from the world-space bounding box and fed them back in as local bounds, so every east-west
 * piece was carved across its own width, and the un-oriented room tried to build itself at world origin
 * no matter where it belonged. Both are invisible in a north-south sewer and neither throws. Every piece
 * here is built with {@link StructurePiece#makeBoundingBox}, which does the axis swap, and carves from
 * its <b>declared</b> local dimensions rather than from the box that came out.
 */
public final class SewerPieces {

    /** How many links deep the recursion goes. Vanilla's mineshaft uses 8 and it sprawls plenty. */
    public static final int MAX_DEPTH = 8;

    /**
     * How far from the root a piece may be placed, in blocks on each horizontal axis.
     *
     * <p>What keeps ONE sewer finite, and only that. <b>It does not stop two sewers meeting</b>, and an
     * earlier version of this javadoc claimed it did: {@code findCollisionPiece} only ever sees the
     * pieces of the sewer currently being built, so nothing checks against a neighbour. Widening the
     * structure set's spacing would enforce it, and was tried - at {@code spacing 20} it also made
     * sewers roughly four hundred times rarer than the rate the spec pins ({@code frequency 0.004},
     * {@code spacing 1}, copied from vanilla's mineshafts rather than guessed), which is a design
     * change and not mine to make. The rarity stands as specified and the overlap is possible but
     * unlikely; it is flagged in the spec as an owner call rather than quietly enforced.
     */
    public static final int RADIUS_CAP = 80;

    /**
     * Walkable space inside a tunnel: three across and three tall.
     *
     * <p>The outer box is this plus a block of shell on each side, which is the correction that
     * matters. This used to be the OUTER size, so after the shell went on, the interior was one wide
     * and two tall - a crawlspace whose entire floor was then filled with leachate, so every corridor
     * sickened the player for its full length with nowhere to stand aside.
     */
    private static final int INNER = 3;

    /** Outer width and height of a tunnel: the walkable bore plus a block of brick either side. */
    private static final int SHELL = INNER + 2;

    /**
     * How long one corridor segment runs before the next piece decides what happens.
     *
     * <p><b>Deliberately not equal to {@link #SHELL}.</b> A square corridor hides an axis bug: local X
     * and local Z swap on an east-west piece, and if the piece is the same size both ways the transposed
     * carve is indistinguishable from the correct one, in-world and in any test. Seven long against five
     * wide makes {@code a_corridor_is_carved_along_its_own_length} able to tell them apart.
     */
    private static final int SEGMENT = 7;

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
     * a mineshaft read as a mineshaft - mostly tunnel, occasional junction, the rare descent.
     */
    private static @Nullable SewerPiece choose(StructurePieceAccessor pieces, RandomSource random,
            int x, int y, int z, Direction facing, int depth) {
        int roll = random.nextInt(100);
        if (roll >= 80) {
            BoundingBox box = SewerPiece.box(x, y, z, facing, SHELL, SHELL, SHELL);
            return pieces.findCollisionPiece(box) == null
                ? new SewerCrossing(depth, box, facing) : null;
        }
        if (roll >= 70) {
            BoundingBox flat = SewerPiece.box(
                x, y, z, facing, SHELL, SHELL + STAIR_DROP, STAIR_RUN);
            BoundingBox box = new BoundingBox(flat.minX(), flat.minY() - STAIR_DROP, flat.minZ(),
                flat.maxX(), flat.maxY() - STAIR_DROP, flat.maxZ());
            return pieces.findCollisionPiece(box) == null
                ? new SewerStairs(depth, box, facing) : null;
        }
        BoundingBox box = SewerPiece.box(x, y, z, facing, SHELL, SHELL, SEGMENT);
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

    // ---------------------------------------------------------------- the pieces

    /** What every sewer piece shares: its type, its box, and how to hollow itself out. */
    public abstract static class SewerPiece extends StructurePiece {

        /**
         * A box of {@code w} across by {@code h} tall by {@code l} long, extending <b>away</b> from the
         * opening it is anchored at.
         *
         * <p><b>Not {@code StructurePiece.makeBoundingBox}, and that distinction is load-bearing.</b>
         * Vanilla's helper always extends in +x/+z from the anchor - {@code direction} only swaps width
         * against depth on the X axis - because it exists for <em>root</em> pieces, which choose their
         * own corner. Chained pieces need the box to run backwards for NORTH and WEST, and using the
         * vanilla helper for them is silently catastrophic: a NORTH branch anchored at
         * {@code minZ - 1} produces a box extending back <em>into</em> its parent, so
         * {@code findCollisionPiece} rejects it and the branch is dropped with no error. Every sewer
         * then grew only south and east - confined to one quadrant, with half of every branch roll
         * wasted - and no test could see it, because a quadrant is smaller than the bound, not larger.
         * Vanilla's own mineshaft builds direction-aware boxes by hand for exactly this reason.
         *
         * <p>The local-to-world mapping lines up with it: {@code getWorldZ} for NORTH is
         * {@code maxZ - localZ}, so local z=0 is the anchor end and the piece runs away from the parent.
         */
        public static BoundingBox box(int x, int y, int z, Direction facing, int w, int h, int l) {
            return switch (facing) {
                case SOUTH -> new BoundingBox(x, y, z, x + w - 1, y + h - 1, z + l - 1);
                case WEST -> new BoundingBox(x - l + 1, y, z, x, y + h - 1, z + w - 1);
                case EAST -> new BoundingBox(x, y, z, x + l - 1, y + h - 1, z + w - 1);
                default -> new BoundingBox(x, y, z - l + 1, x + w - 1, y + h - 1, z);
            };
        }

        protected SewerPiece(StructurePieceType type, int depth, BoundingBox box) {
            super(type, depth, box);
        }

        protected SewerPiece(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }

        /**
         * Nothing to save. The box, the depth and the orientation are written by {@link StructurePiece}
         * itself, and a sewer piece has no state of its own.
         */
        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        }

        /**
         * Line a tunnel: floor, ceiling and two side walls, with <b>both ends left open</b>.
         *
         * <p>The open ends are the whole point, and their absence was the worst of the three geometry
         * bugs here. {@code generateBox}'s two-state form paints all six faces with the boundary state,
         * and every child is anchored one block past its parent, so pieces came out as sealed brick
         * boxes separated by two solid layers - a structure you could stand inside exactly one segment
         * of and never walk through. Vanilla's corridors are open end to end for the same reason.
         *
         * <p>Coordinates are <b>local and declared</b>, never derived from {@code boundingBox}: for an
         * east-west piece local X is world Z, so measuring the box and feeding the result back in
         * carves the tunnel across its own width.
         */
        protected void line(WorldGenLevel level, BoundingBox limit, int w, int h, int l) {
            // Hollow everything first, ends included - that is what connects one piece to the next.
            this.generateBox(level, limit, 0, 0, 0, w - 1, h - 1, l - 1,
                SewerPalette.HOLLOW, SewerPalette.HOLLOW, false);
            this.generateBox(level, limit, 0, 0, 0, w - 1, 0, l - 1,
                SewerPalette.WALL, SewerPalette.WALL, false);
            this.generateBox(level, limit, 0, h - 1, 0, w - 1, h - 1, l - 1,
                SewerPalette.WALL, SewerPalette.WALL, false);
            this.generateBox(level, limit, 0, 1, 0, 0, h - 2, l - 1,
                SewerPalette.WALL, SewerPalette.WALL, false);
            this.generateBox(level, limit, w - 1, 1, 0, w - 1, h - 2, l - 1,
                SewerPalette.WALL, SewerPalette.WALL, false);
            // AND CUT THE DOORWAY BACK THROUGH THE PARENT. A child is anchored one block past its
            // parent, so a piece that turns left or right starts on the far side of the parent's side
            // wall and every such branch was sealed behind a solid brick layer - only straight-ahead
            // children connected, because both end planes are hollow. Hollowing the plane one step
            // BEFORE this piece begins carves through whatever the parent put there.
            //
            // Order makes this safe: a parent is added before its children and postProcesses first, so
            // the child always has the last word on the shared face.
            this.generateBox(level, limit, 1, 1, -1, w - 2, h - 2, -1,
                SewerPalette.HOLLOW, SewerPalette.HOLLOW, false);
        }

        /**
         * Sink the channel into the floor down the middle of the run, leaving a walkway either side.
         *
         * <p>The fluid replaces floor blocks rather than sitting on them, so a player walks the brick
         * strips at the sides with the leachate ankle-deep beside them. With a three-wide bore that is
         * one channel and two walkways; the earlier one-wide bore had room for neither, so the channel
         * covered the whole floor and the corridor was a Hunger tax end to end.
         */
        protected void channel(WorldGenLevel level, BoundingBox limit, int w, int l) {
            for (int z = 1; z < l - 1; z++) {
                this.placeBlock(level, SewerPalette.FLUID, w / 2, 0, z, limit);
            }
        }

        /** Sparse cobwebs under the ceiling: the mineshaft parallel, and the only source in the game. */
        protected void cobwebs(WorldGenLevel level, BoundingBox limit, RandomSource random,
                int w, int h, int l) {
            for (int x = 1; x < w - 1; x++) {
                for (int z = 1; z < l - 1; z++) {
                    if (random.nextInt(24) == 0) {
                        this.placeBlock(level, SewerPalette.WEB, x, h - 2, z, limit);
                    }
                }
            }
        }
    }

    /** A straight run of tunnel, and the piece that does the branching. */
    public static class SewerCorridor extends SewerPiece {

        public SewerCorridor(int depth, BoundingBox box, Direction facing) {
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
         * piece: an offset on the next opening, no extra piece type and no extra geometry.
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
            this.line(level, limit, SHELL, SHELL, SEGMENT);
            this.channel(level, limit, SHELL, SEGMENT);
            this.cobwebs(level, limit, random, SHELL, SHELL, SEGMENT);
        }
    }

    /** A junction: same bore, square, and it opens every way except back. */
    public static class SewerCrossing extends SewerPiece {

        public SewerCrossing(int depth, BoundingBox box, Direction facing) {
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
            this.line(level, limit, SHELL, SHELL, SHELL);
            this.channel(level, limit, SHELL, SHELL);
            // A grate in the ceiling, which is what a sewer junction has and what stops a crossing
            // reading as nothing more than a wide bit of corridor.
            this.placeBlock(level, SewerPalette.GRATE, SHELL / 2, SHELL - 1, SHELL / 2, limit);
        }
    }

    /** The descent: down five over eight, on steps you can actually walk. */
    public static class SewerStairs extends SewerPiece {

        public SewerStairs(int depth, BoundingBox box, Direction facing) {
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
            // No channel: a slope is the one place the fluid would actually run, and a flooded
            // stairwell is both a generation-time flood risk and the head-height column that made
            // canDrown a problem in the first place.
            this.line(level, limit, SHELL, SHELL + STAIR_DROP, STAIR_RUN);
            // AND ACTUAL STEPS. The first version hollowed the shaft and placed nothing in it, so
            // entering one was a five-block fall into a dead end - the palette had no ladder, no slab
            // and no stair in it at all, so there was nothing to climb back out on either.
            BlockState step = SewerPalette.STEP.setValue(
                BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
            // A LANDING FIRST. The box is shifted down by the drop, so local y=STAIR_DROP is the
            // parent corridor's floor - and the entrance plane is hollow all the way down, which left a
            // five-deep pit in the doorway. Walking out of the parent meant falling into the cavity
            // under the staircase rather than onto the top step.
            for (int x = 1; x < SHELL - 1; x++) {
                this.placeBlock(level, SewerPalette.WALL, x, STAIR_DROP, 0, limit);
            }
            for (int z = 1; z < STAIR_RUN - 1; z++) {
                int y = STAIR_DROP - (z * STAIR_DROP) / (STAIR_RUN - 2);
                for (int x = 1; x < SHELL - 1; x++) {
                    this.placeBlock(level, step, x, y, z, limit);
                }
            }
        }
    }

    /** The root chamber a sewer grows out of. Wider, taller, and it opens all four ways. */
    public static class SewerRoom extends SewerPiece {

        /**
         * Rooted at y=50 the way vanilla's mineshaft room is, then moved as a whole by
         * {@code SewerStructure} once the tree is built.
         */
        public SewerRoom(int depth, RandomSource random, int x, int z) {
            super(RCStructures.SEWER_ROOM.get(), depth, new BoundingBox(
                x, 50, z, x + 9 + random.nextInt(5), 57, z + 9 + random.nextInt(5)));
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

        /**
         * <b>Absolute coordinates, because the room has no orientation.</b> With {@code getOrientation()}
         * null, {@code getWorldX/Y/Z} hand back whatever they are given, so local coordinates ARE world
         * coordinates - passing {@code 0..width} built the room at world origin no matter where its box
         * was, which for a sewer several hundred blocks out meant the root chamber never appeared at all
         * and its corridors dead-ended into solid rock. Vanilla's own un-oriented {@code MineShaftRoom}
         * passes its bounding box straight through for exactly this reason.
         */
        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
            BoundingBox box = this.boundingBox;
            this.generateBox(level, limit, box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ(), SewerPalette.HOLLOW, SewerPalette.HOLLOW, false);
            this.generateBox(level, limit, box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.minY(), box.maxZ(), SewerPalette.WALL, SewerPalette.WALL, false);
            this.generateBox(level, limit, box.minX(), box.maxY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ(), SewerPalette.WALL, SewerPalette.WALL, false);
            // Vertical walls too, so the chamber is brick rather than a brick floor and ceiling with
            // raw deepslate sides - which is what it was, and it showed at every junction where the
            // corridors' own brick met bare stone. Safe to wall now that a child cuts its own doorway
            // back through whatever its parent placed.
            for (int y = box.minY() + 1; y < box.maxY(); y++) {
                this.generateBox(level, limit, box.minX(), y, box.minZ(), box.minX(), y, box.maxZ(),
                    SewerPalette.WALL, SewerPalette.WALL, false);
                this.generateBox(level, limit, box.maxX(), y, box.minZ(), box.maxX(), y, box.maxZ(),
                    SewerPalette.WALL, SewerPalette.WALL, false);
                this.generateBox(level, limit, box.minX(), y, box.minZ(), box.maxX(), y, box.minZ(),
                    SewerPalette.WALL, SewerPalette.WALL, false);
                this.generateBox(level, limit, box.minX(), y, box.maxZ(), box.maxX(), y, box.maxZ(),
                    SewerPalette.WALL, SewerPalette.WALL, false);
            }
            // A pool rather than a channel: the room is the one place the fluid is allowed to be wide.
            // Inset by two so it never reaches a wall a corridor might open through.
            for (int x = box.minX() + 2; x <= box.maxX() - 2; x++) {
                for (int z = box.minZ() + 2; z <= box.maxZ() - 2; z++) {
                    this.placeBlock(level, SewerPalette.FLUID, x, box.minY(), z, limit);
                }
            }
            // The chamber is where the sewer is occupied from. One spawner per sewer, in the room
            // rather than at a corridor mouth, so meeting it is a thing you walk into rather than
            // something that meets you at the entrance.
            // ON BRICK, NOT IN THE POOL. The pool fills minX+2..maxX-2, and the box centre is inside
            // that inset by construction, so a spawner at the centre stood in the leachate - you had to
            // wade into the Hunger fluid to reach or break it. One block in from the wall is dry.
            BlockPos seat = new BlockPos(box.minX() + 1, box.minY() + 1, box.getCenter().getZ());
            if (limit.isInside(seat)) {
                level.setBlock(seat, SewerPalette.SPAWNER, 2);
                if (level.getBlockEntity(seat) instanceof net.minecraft.world.level.block.entity
                        .SpawnerBlockEntity spawner) {
                    spawner.setEntityId(net.minecraft.world.entity.EntityType.DROWNED, random);
                }
            }
            placeTurtles(level, limit, box);
        }
    }

    /**
     * Turtles, placed as <b>entities at generation</b> rather than spawned.
     *
     * <p>Owner call: turtles stay in the sewer. They cannot arrive any other way -
     * {@code Turtle.checkTurtleSpawnRules} demands {@code y < seaLevel + 4}, and this world's sea level
     * is <b>-64</b>, so the height test alone requires y &lt; -60; it also wants sand, which this world
     * has none of. Unlike the drowned there is no spawner branch to lean on, so a spawner would need
     * {@code custom_spawn_rules} to bypass the predicate entirely - and a spawner endlessly producing a
     * passive animal reads wrong anyway. Placing them directly also makes the population <b>finite</b>,
     * which is what the spec wanted: they cannot breed here (no seagrass) or lay eggs (no sand).
     *
     * <p><b>Fixed positions, not rolled ones.</b> {@code postProcess} runs <em>once per chunk</em> the
     * piece overlaps, each time with a different {@code limit} and a different {@code RandomSource} - and
     * a room is 10 to 14 blocks across, so it straddles two to four chunks in most placements. Rolling
     * {@code 2 + rand(3)} positions per call meant each chunk rolled its own fresh set over the whole
     * room and kept whichever happened to fall inside itself: the total was a random sum from 0 to 12
     * rather than 2-4, and a room split four ways could quite easily produce a sewer with <b>no turtles
     * at all</b> - exactly the ships-empty-in-silence failure this was written to prevent. Deriving the
     * spots from the room's own box makes every chunk pass agree on them, and the {@code isInside} guard
     * then places each one exactly once. It is the same shape as the spawner beside it, which was
     * already correct.
     *
     * <p><b>And {@code finalizeSpawn} rather than a bare {@code addFreshEntity}.</b> A turtle's
     * {@code homePos} defaults to {@code BlockPos.ZERO} and is only ever set there, so a turtle added
     * without it believes home is world origin - {@code TurtleGoHomeGoal} then fires on any position
     * more than 64 blocks away, which for a sewer hundreds of blocks out is always, and the turtles
     * walk out of the chamber toward spawn. It survives a reload too, because the wrong home is what
     * gets saved. Vanilla's {@code SwampHutPiece} calls {@code finalizeSpawn} on its witch and cat for
     * exactly this reason.
     */
    private static void placeTurtles(WorldGenLevel level, BoundingBox limit, BoundingBox room) {
        int midZ = room.getCenter().getZ();
        int midX = room.getCenter().getX();
        BlockPos[] spots = {
            new BlockPos(midX - 2, room.minY() + 1, midZ - 2),
            new BlockPos(midX + 2, room.minY() + 1, midZ - 2),
            new BlockPos(midX - 2, room.minY() + 1, midZ + 2),
            new BlockPos(midX + 2, room.minY() + 1, midZ + 2),
        };
        for (BlockPos at : spots) {
            if (!limit.isInside(at)) {
                continue;   // a different chunk pass owns this one and will place it
            }
            var turtle = net.minecraft.world.entity.EntityType.TURTLE.create(
                level.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
            if (turtle == null) {
                continue;
            }
            turtle.snapTo(at.getX() + 0.5, (double) at.getY(), at.getZ() + 0.5, 0F, 0F);
            turtle.finalizeSpawn(level, level.getCurrentDifficultyAt(at),
                net.minecraft.world.entity.EntitySpawnReason.STRUCTURE, null);
            // Explicitly, not just as a side effect of finalizeSpawn. homePos is private with no
            // getter, so no test can prove it was set - and the failure it causes is silent and
            // permanent, since the wrong home is what gets saved. Saying it outright costs one line.
            turtle.setHomePos(at);
            turtle.setPersistenceRequired();
            level.addFreshEntity(turtle);
        }
    }
}
