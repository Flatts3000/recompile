package com.flatts.recompile.content.worldgen.sewer;

import com.flatts.recompile.registry.RCStructures;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    /**
     * What a sewer barrel holds (#90 phase 4).
     *
     * <p>Set on the BlockEntity rather than rolled here: a table attached to a container is rolled the
     * first time a player opens it, so nothing is decided at generation, two players on one seed do not
     * see each other's rolls, and the contents stay a datapack question - which is where the balance of
     * this belongs.
     */
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot
        .LootTable> BARREL_LOOT = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.LOOT_TABLE,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                com.flatts.recompile.Recompile.MOD_ID, "chests/sewer"));

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
    /**
     * Put the sump at the lowest end of the finished tree.
     *
     * <p><b>The tree decides where it goes, not the structure.</b> A sewer drains downhill, so the low
     * point is wherever the stairs happened to descend furthest - which is only knowable once the graph
     * is built. Attaching it anywhere else would make the one room whose position is physically
     * determined into an arbitrary one.
     *
     * <p>It goes through {@code findCollisionPiece} by hand here rather than through {@code grow},
     * because {@code grow} chooses a piece type at random and this one is not up for a vote.
     */
    public static void attachSump(StructurePiece root, StructurePiecesBuilder pieces,
            RandomSource random) {
        // DEEPEST FIRST, then the next deepest. Trying only the single lowest piece left 46 sewers in
        // 200 without a sump: it is a nine-block room and the low end of a sewer is usually the busy
        // end, so all four of its sides collide. Walking down the list keeps the fiction - the sump is
        // still at the bottom of the system - while giving it somewhere to actually fit.
        List<StructurePiece> candidates = new java.util.ArrayList<>();
        for (StructurePiece piece : collect(pieces)) {
            if (!(piece instanceof SewerRoom) && !(piece instanceof SewerEntrance)) {
                candidates.add(piece);
            }
        }
        candidates.sort(java.util.Comparator.comparingInt(piece -> piece.getBoundingBox().minY()));
        for (StructurePiece candidate : candidates) {
            if (trySump(root, pieces, candidate)) {
                return;
            }
        }
    }

    /** Attempt the sump against one piece, on each of its four sides. */
    private static boolean trySump(StructurePiece root, StructurePiecesBuilder pieces,
            StructurePiece against) {
        BoundingBox from = against.getBoundingBox();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int x = switch (side) {
                case WEST -> from.minX() - 1;
                case EAST -> from.maxX() + 1;
                default -> from.minX();
            };
            int z = switch (side) {
                case NORTH -> from.minZ() - 1;
                case SOUTH -> from.maxZ() + 1;
                default -> from.minZ();
            };
            BoundingBox box = SewerPiece.box(x, from.minY(), z, side,
                SewerSump.SIZE, SewerSump.TALL, SewerSump.SIZE);
            if (Math.abs(x - root.getBoundingBox().minX()) <= RADIUS_CAP
                    && Math.abs(z - root.getBoundingBox().minZ()) <= RADIUS_CAP
                    && pieces.findCollisionPiece(box) == null) {
                pieces.addPiece(new SewerSump(against.getGenDepth() + 1, box, side));
                return true;
            }
        }
        return false;
    }

    /**
     * Make sure the sewer has at least one access chamber, since all of its loot is in them.
     *
     * <p>Corridors open these on a roll, which measured <b>192 sewers in 200</b>. The eight that missed
     * generated with nothing to find, because moving the barrels out of the root chamber traded a
     * guarantee for a placement - the right trade, made carelessly. This walks the finished tree and
     * forces one only if the roll produced none, so the common case stays organic and the fallback never
     * shows.
     *
     * <p>It goes through {@code growAccess}, so the forced room is collision-checked like any other.
     */
    public static void forceAccessChamber(StructurePiece root, StructurePiecesBuilder pieces,
            RandomSource random) {
        List<StructurePiece> built = collect(pieces);
        for (StructurePiece piece : built) {
            if (piece instanceof SewerAccessChamber) {
                return;
            }
        }
        // PAST THE FIRST LINK FIRST. The first corridor in build order is the one leaving the root
        // chamber, and hanging the store room off it is exactly the placement the roll's depth >= 2
        // guard exists to prevent - a store at the bottom of the ladder is the first thing you see.
        // The earlier version walked build order and landed there every time the fallback fired.
        if (attachSomewhere(root, pieces, built, 2)) {
            return;
        }
        // Then anywhere at all. A sewer with its loot in an awkward place beats a sewer with no loot,
        // and this is the branch that turns a best-effort into a guarantee: crossings and stairs count
        // now, not only corridors, because 30% of pieces are neither.
        if (attachSomewhere(root, pieces, built, 0)) {
            return;
        }
        // LAST RESORT: the root chamber, which always exists. Reached only when every other piece in
        // the sewer is boxed in, and it is the one placement the design would rather avoid - but the
        // alternative is a sewer that pays out nothing, which is the failure this method is for.
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (growAccess(root, pieces, root.getBoundingBox(), side, 2)) {
                return;
            }
        }
    }

    /** Try every side of every eligible piece at or past {@code minDepth}, deepest links first. */
    private static boolean attachSomewhere(StructurePiece root, StructurePiecesBuilder pieces,
            List<StructurePiece> built, int minDepth) {
        List<StructurePiece> candidates = new ArrayList<>();
        for (StructurePiece piece : built) {
            if (piece instanceof SewerRoom || piece instanceof SewerEntrance
                    || piece instanceof SewerDen || piece.getGenDepth() < minDepth) {
                continue;
            }
            candidates.add(piece);
        }
        candidates.sort(Comparator.comparingInt(piece -> -piece.getGenDepth()));
        for (StructurePiece candidate : candidates) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (growAccess(root, pieces, candidate.getBoundingBox(), side,
                        Math.max(minDepth, candidate.getGenDepth()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The builder's current contents, for the forced-room search. */
    private static java.util.List<StructurePiece> collect(StructurePiecesBuilder pieces) {
        java.util.List<StructurePiece> out = new ArrayList<>();
        pieces.build().pieces().forEach(out::add);
        return out;
    }

    /**
     * Try to open an access chamber off one side of a corridor.
     *
     * <p>Goes through {@code findCollisionPiece} like any other child, so a room that would land on the
     * sewer's own geometry is simply not built - no hand-written overlap test required, which is the
     * whole argument for attaching through the graph rather than from the structure.
     */
    static boolean growAccess(StructurePiece root, StructurePieceAccessor pieces,
            BoundingBox from, Direction side, int depth) {
        if (depth > MAX_DEPTH) {
            return false;
        }
        int x = switch (side) {
            case WEST -> from.minX() - 1;
            case EAST -> from.maxX() + 1;
            default -> from.minX();
        };
        int z = switch (side) {
            case NORTH -> from.minZ() - 1;
            case SOUTH -> from.maxZ() + 1;
            default -> from.minZ();
        };
        BoundingBox box = SewerPiece.box(x, from.minY(), z, side,
            SewerAccessChamber.SIZE, SewerAccessChamber.TALL, SewerAccessChamber.SIZE);
        if (Math.abs(x - root.getBoundingBox().minX()) > RADIUS_CAP
                || Math.abs(z - root.getBoundingBox().minZ()) > RADIUS_CAP
                || pieces.findCollisionPiece(box) != null) {
            return false;
        }
        pieces.addPiece(new SewerAccessChamber(depth + 1, box, side));
        return true;
    }

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
            // THE WET COURSE. Decay follows water, so the one wall row level with the channel is the row
            // that greens and cracks while everything above it stays clean brick. Keyed on height rather
            // than on a roll: a scatter of mossy blocks up a dry wall reads as speckling, and a solid
            // green course at floor level reads as a waterline.
            for (int z = 0; z < l; z++) {
                for (int x : new int[]{0, w - 1}) {
                    this.placeBlock(level, weathered(z * 31 + x), x, 1, z, limit);
                }
            }
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
         * Which weathered block a wet-course cell gets, from its own position.
         *
         * <p>Deterministic on purpose. {@code postProcess} runs once per chunk a piece overlaps, each
         * time with a fresh {@code RandomSource}, so a rolled wall would come out different on either
         * side of a chunk boundary - a seam straight down the middle of a corridor.
         */
        protected BlockState weathered(int key) {
            // FOLD IN THE PIECE'S OWN POSITION. Deterministic is required - postProcess re-runs per
            // overlapped chunk, so a rolled wall would seam down a chunk boundary - but deterministic is
            // not the same as uniform, and the first version keyed on local coordinates alone. Local
            // coordinates are identical for every corridor ever generated, so every corridor in every
            // world came out with a byte-identical moss pattern: not weathering, tiling. The spawner a
            // few methods away already had this right.
            int seed = key + this.boundingBox.minX() * 31 + this.boundingBox.minZ() * 17;
            int pick = Math.floorMod(seed, 5);
            if (pick == 0) {
                return SewerPalette.CRACKED_COURSE;
            }
            return pick <= 2 ? SewerPalette.WET_COURSE : SewerPalette.WALL;
        }

        /**
         * Silt along the channel, and the odd mushroom on it.
         *
         * <p><b>Where the flow slows.</b> Silt settles at the ends of a run rather than along it, which
         * is why this works the first and last cells rather than scattering down the middle - a sewer
         * silts up at its corners, not evenly.
         */
        protected void silt(WorldGenLevel level, BoundingBox limit, int w, int l) {
            // ARITHMETIC THAT CAN ACTUALLY FIRE. The first version could not place two of its three
            // blocks at all: with the only caller passing l=7 the loop saw z in {1, 5}, so key = z*17+dx
            // was always even and the fine deposit never won its ternary, and the growth test wanted
            // z divisible by 4, which neither value is. Both were declared, documented, added to the
            // palette, and dead - and nothing could see it, because the palette walk only asks what a
            // block IS, never whether it is reached.
            //
            // Seeded from the piece's own box for the same reason weathered() is: identical local
            // coordinates in every corridor would put the gravel in the same two cells forever.
            int mid = w / 2;
            int seed = this.boundingBox.minX() * 31 + this.boundingBox.minZ() * 17;
            for (int z : new int[]{1, l - 2}) {
                for (int dx = -1; dx <= 1; dx += 2) {
                    int key = seed + z * 7 + dx;
                    if (Math.floorMod(key, 3) != 0) {
                        continue;
                    }
                    this.placeBlock(level, Math.floorMod(key, 2) == 0
                        ? SewerPalette.SILT : SewerPalette.FINE_SILT, mid + dx, 0, z, limit);
                }
                if (Math.floorMod(seed + z, 4) == 0) {
                    this.placeBlock(level, SewerPalette.GROWTH, mid + 1, 1, z, limit);
                }
            }
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
            // AND SOMETIMES A DOOR IN THE SIDE. An access chamber hangs off the run rather than
            // interrupting it, which is where a real one is - you pass the door, you do not walk through
            // the room.
            //
            // Past the first link only: a store room at the bottom of the ladder is the first thing you
            // see, and the point of it is to reward looking. Rolling here is safe in a way rolling in
            // postProcess is not - addChildren runs ONCE, while the tree is built, not per chunk.
            if (depth >= 2 && random.nextInt(4) == 0) {
                Direction side = random.nextBoolean()
                    ? facing.getClockWise() : facing.getCounterClockWise();
                growAccess(root, pieces, box, side, depth);
            }
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
            this.line(level, limit, SHELL, SHELL, SEGMENT);
            this.channel(level, limit, SHELL, SEGMENT);
            this.silt(level, limit, SHELL, SEGMENT);
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
            // NO SPAWNER HERE ANY MORE. Junctions carried one past depth 2 on an even box-hash, which
            // was a stand-in for a guarantee the graph could not give - measured, roughly one sewer in
            // five had none at all. The sump provides it now, deterministically and for a reason that is
            // not arithmetic: standing water is where drowned accumulate. Two mechanisms for one threat
            // is one more than the fiction supports.
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

    /**
     * A habitat pocket off the chamber: a small room floored in something an animal actually wants.
     *
     * <p><b>Owner call, 2026-08-17: a module each, sand for the turtles and mud for the frogs.</b> They
     * used to be placed straight into the leachate pool in the middle of the chamber, which meant every
     * sewer generated with its animals standing in the Hunger fluid, permanently - and it read like a
     * zoo, because the drowned were in there with them.
     *
     * <p>The substrate is not decoration. {@code #minecraft:frogs_spawnable_on} is grass block, mud and
     * the two mangrove roots, so mud is the one surface in that tag a sewer could hold; and
     * {@code TurtleEggBlock.onSand} is half of vanilla's turtle rule. The animals are standing on the
     * ground their own game logic names. Neither becomes renewable - a turtle also needs
     * {@code y < seaLevel + 4} against a sea level of -64, and a frog needs light this place does not
     * have.
     */
    public abstract static class SewerDen extends SewerPiece {

        protected SewerDen(StructurePieceType type, int depth, BoundingBox box) {
            super(type, depth, box);
        }

        protected SewerDen(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
        }

        /** What the floor is made of, and therefore which animal this is for. */
        protected abstract BlockState bed();

        /** How many to put in it. */
        protected abstract int population();

        /** The animal itself. */
        protected abstract net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> resident();

        /**
         * Which of the den's own walls faces the chamber, and therefore where its door goes.
         *
         * <p>A den is placed flush against one of the chamber's walls, so exactly one of its four sides
         * is shared masonry - carving anywhere else opens the den into solid rock.
         */
        protected abstract Direction doorSide();

        /**
         * Absolute coordinates, like the chamber and the entrance shaft: a den has no orientation, and
         * with {@code getOrientation()} null {@code getWorldX/Y/Z} return what they are given.
         */
        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
            BoundingBox box = this.boundingBox;
            this.generateBox(level, limit, box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ(), SewerPalette.HOLLOW, SewerPalette.HOLLOW, false);
            // Shell it, then floor it in the substrate. The floor is the whole point of the room.
            this.generateBox(level, limit, box.minX(), box.maxY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ(), SewerPalette.WALL, SewerPalette.WALL, false);
            this.generateBox(level, limit, box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.minY(), box.maxZ(), this.bed(), this.bed(), false);
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
            // NO POOL. A den's interior is twelve walkable cells and the pool took four of them, so an
            // animal wandering at random spent a third of its life in leachate and being sickened by
            // RCLeachateContact - a smaller version of the exact problem the dens were built to fix.
            // The chamber next door is the wet room; this one is the bank.
            //
            // AND A DOORWAY, which is the half that made the whole feature invisible. Every wall was
            // written and nothing was ever carved, so each den shipped as an airtight box with three
            // turtles sealed inside it. Nobody could enter one, see one, or know it was there.
            int doorY = box.minY() + 1;
            for (int step = 0; step < 2; step++) {
                BlockPos door = switch (this.doorSide()) {
                    case WEST -> new BlockPos(box.minX(), doorY + step, box.getCenter().getZ());
                    case EAST -> new BlockPos(box.maxX(), doorY + step, box.getCenter().getZ());
                    case NORTH -> new BlockPos(box.getCenter().getX(), doorY + step, box.minZ());
                    default -> new BlockPos(box.getCenter().getX(), doorY + step, box.maxZ());
                };
                if (limit.isInside(door)) {
                    level.setBlock(door, SewerPalette.HOLLOW, Block.UPDATE_CLIENTS);
                }
            }
            // A den is a room a player looks into, and lighting it keeps its animals visible and its
            // floor unspawnable - both of which are the point of having built it.
            BlockPos lamp = new BlockPos(box.getCenter().getX(), box.maxY() - 1, box.getCenter().getZ());
            if (limit.isInside(lamp)) {
                level.setBlock(lamp, SewerPalette.LIGHT, Block.UPDATE_CLIENTS);
            }
            for (int i = 0; i < this.population(); i++) {
                BlockPos at = new BlockPos(box.minX() + 1 + i, box.minY() + 1, box.minZ() + 1);
                if (!limit.isInside(at)) {
                    continue;
                }
                var mob = this.resident().create(
                    level.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
                if (mob == null) {
                    continue;
                }
                mob.snapTo(at.getX() + 0.5, (double) at.getY(), at.getZ() + 0.5, 0F, 0F);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at),
                    net.minecraft.world.entity.EntitySpawnReason.STRUCTURE, null);
                if (mob instanceof net.minecraft.world.entity.animal.turtle.Turtle turtle) {
                    // Explicitly: homePos is private with no getter, so nothing can assert it, and a
                    // turtle that believes home is world origin walks out of the den forever.
                    turtle.setHomePos(at);
                }
                mob.setPersistenceRequired();
                level.addFreshEntity(mob);
            }
        }
    }

    /** Sand, and the turtles that live on it. */
    public static class SewerTurtleDen extends SewerDen {

        public SewerTurtleDen(int depth, BoundingBox box) {
            super(RCStructures.SEWER_TURTLE_DEN.get(), depth, box);
        }

        public SewerTurtleDen(CompoundTag tag) {
            super(RCStructures.SEWER_TURTLE_DEN.get(), tag);
        }

        @Override
        protected BlockState bed() {
            return SewerPalette.TURTLE_BED;
        }

        @Override
        protected int population() {
            return 3;
        }

        @Override
        protected net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> resident() {
            return net.minecraft.world.entity.EntityType.TURTLE;
        }

        /** It sits against the chamber's east wall, so its own west side is the shared one. */
        @Override
        protected Direction doorSide() {
            return Direction.WEST;
        }
    }

    /** Mud, and the frogs that live on it. */
    public static class SewerFrogDen extends SewerDen {

        public SewerFrogDen(int depth, BoundingBox box) {
            super(RCStructures.SEWER_FROG_DEN.get(), depth, box);
        }

        public SewerFrogDen(CompoundTag tag) {
            super(RCStructures.SEWER_FROG_DEN.get(), tag);
        }

        @Override
        protected BlockState bed() {
            return SewerPalette.FROG_BED;
        }

        @Override
        protected int population() {
            return 2;
        }

        @Override
        protected net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> resident() {
            return net.minecraft.world.entity.EntityType.FROG;
        }

        /** It sits against the chamber's south wall, so its own north side is the shared one. */
        @Override
        protected Direction doorSide() {
            return Direction.NORTH;
        }
    }

    /**
     * An access chamber: a dry room off the main run (#90 improvements, phase 2).
     *
     * <p><b>Why a sewer has one.</b> Somebody had to maintain this, and maintenance needs somewhere to
     * stand that is not the channel - valve chambers, inspection points, a place the crew left their
     * things. It also answers a question the loot was previously dodging: the barrels used to sit in the
     * entrance chamber because that is where the code could put them, not because anyone would store
     * anything at the foot of a ladder. They live here now, in a room that explains them.
     *
     * <p><b>It attaches through the graph, which the dens do not.</b> Being a child means
     * {@code findCollisionPiece} sees it and rejects it if it would land on something - the guarantee
     * the dens had to buy with hand-written geometry tests, because pieces attached directly by the
     * structure are invisible to collision and win every overlap silently. Anything that can be a child
     * should be.
     *
     * <p>Dry and lit, per phase 1's rule: this is a room a person worked in, so it has a lamp, and a lit
     * room is an unspawnable one - which is what you want of the room holding the reward.
     */
    public static class SewerAccessChamber extends SewerPiece {

        /** Outer size. Bigger than a corridor so it reads as a room, small enough to sit beside one. */
        static final int SIZE = 7;
        static final int TALL = 6;

        public SewerAccessChamber(int depth, BoundingBox box, Direction facing) {
            super(RCStructures.SEWER_ACCESS_CHAMBER.get(), depth, box);
            this.setOrientation(facing);
        }

        public SewerAccessChamber(CompoundTag tag) {
            super(RCStructures.SEWER_ACCESS_CHAMBER.get(), tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
            // line() leaves both ends open, which is what connects this back to the corridor that
            // spawned it - the same doorway mechanism every other piece uses, so no special case.
            this.line(level, limit, SIZE, TALL, SIZE);
            // AND A BACK WALL. line() leaves BOTH end planes open, which is what lets corridors chain -
            // but this room never has a child, so its rear face was guaranteed to be a five-by-four hole
            // onto bare terrain, directly behind the barrels. A dead-ending corridor has the same
            // artefact by accident; here it was by construction, in the one room the feature is about.
            this.generateBox(level, limit, 0, 0, SIZE - 1, SIZE - 1, TALL - 1, SIZE - 1,
                SewerPalette.WALL, SewerPalette.WALL, false);
            // NO CHANNEL and NO FLUID. The point of the room is that it is the dry place.
            BlockPos lamp = new BlockPos(this.getWorldX(SIZE / 2, SIZE / 2), this.getWorldY(TALL - 2),
                this.getWorldZ(SIZE / 2, SIZE / 2));
            if (limit.isInside(lamp)) {
                level.setBlock(lamp, SewerPalette.LIGHT, Block.UPDATE_CLIENTS);
            }
            // The stores. Two barrels against the back wall, where a crew would have left them.
            for (int dx : new int[]{1, SIZE - 2}) {
                BlockPos at = new BlockPos(this.getWorldX(dx, SIZE - 2), this.getWorldY(1),
                    this.getWorldZ(dx, SIZE - 2));
                if (!limit.isInside(at)) {
                    continue;
                }
                level.setBlock(at, SewerPalette.BARREL, Block.UPDATE_CLIENTS);
                net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(
                    level, random, at, BARREL_LOOT);
            }
        }
    }

    /**
     * The sump: the bottom of the system (#90 improvements, phase 3).
     *
     * <p><b>Its position is not a design choice.</b> Everything the channels carry has to go somewhere,
     * and it goes to the lowest point - so the sump belongs at the deepest end of the piece tree,
     * wherever the stairs happened to descend furthest. Putting it anywhere else would be the
     * ham-fisted version of exactly this feature: a room placed because a room was wanted.
     *
     * <p>Three things fall out of that placement rather than being added to it:
     *
     * <ul>
     *   <li><b>The deep leachate is not a hazard we installed</b>, it is what a low point in a drainage
     *       system contains. That is the difference between a hazard and a trap - and it is only lethal
     *       at all because {@code RCLeachateContact} drains air when the eye is under, which the fluid's
     *       own {@code canDrown} flag never did.
     *   <li><b>The drowned belong here.</b> They are what accumulates in standing water, which is a
     *       better reason for the guaranteed spawner than "somewhere deterministic was needed".
     *   <li><b>It is dark.</b> Nobody maintained the bottom, so phase 1's lighting rule keeps out on its
     *       own terms rather than as a spawn concession.
     * </ul>
     *
     * <p><b>Telegraphed, and that is a requirement rather than a nicety.</b> Leachate is opaque and this
     * room is unlit, so the drop is stepped rather than sheer: a ledge at the entrance, then the deep.
     * A drop you cannot see is a death with no decision in front of it.
     */
    public static class SewerSump extends SewerPiece {

        static final int SIZE = 9;
        static final int TALL = 7;

        /** How deep the standing water is. Two, so it is over a head - the point of the room. */
        static final int DEPTH = 2;

        public SewerSump(int depth, BoundingBox box, Direction facing) {
            super(RCStructures.SEWER_SUMP.get(), depth, box);
            this.setOrientation(facing);
        }

        public SewerSump(CompoundTag tag) {
            super(RCStructures.SEWER_SUMP.get(), tag);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
            this.line(level, limit, SIZE, TALL, SIZE);
            // A LEDGE FIRST. The entrance third stays at floor level so a player steps onto solid brick
            // and sees the water before deciding to enter it. Everything past it is the pool.
            for (int x = 1; x < SIZE - 1; x++) {
                for (int z = 3; z < SIZE - 1; z++) {
                    for (int d = 0; d < DEPTH; d++) {
                        this.placeBlock(level, SewerPalette.HOLLOW, x, d + 1, z, limit);
                    }
                    this.placeBlock(level, SewerPalette.FLUID, x, 0, z, limit);
                    this.placeBlock(level, SewerPalette.FLUID, x, 1, z, limit);
                }
            }
            // THE SPAWNER, and this is the room's other job: a sewer can otherwise generate with no
            // drowned at all, because junctions carry one only past depth 2 and only when their box
            // hashes even. Standing water is where drowned accumulate, so the guarantee and the fiction
            // are the same fact.
            BlockPos seat = new BlockPos(this.getWorldX(SIZE / 2, 1), this.getWorldY(1),
                this.getWorldZ(SIZE / 2, 1));
            if (limit.isInside(seat)) {
                level.setBlock(seat, SewerPalette.SPAWNER, Block.UPDATE_CLIENTS);
                if (level.getBlockEntity(seat) instanceof net.minecraft.world.level.block.entity
                        .SpawnerBlockEntity spawner) {
                    spawner.setEntityId(net.minecraft.world.entity.EntityType.DROWNED, random);
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
            // THE LADDER THE SHAFT CANNOT PLACE. The entrance stops at the ceiling, because lining it
            // any lower would seal the ladder inside a brick tube standing in the middle of the room. So
            // the chamber runs the ladder from its floor up through its own ceiling to meet the shaft -
            // without this the shaft's lowest rung sat six blocks above the player's feet, directly over
            // the leachate pool, and the only way in was a one-way drop into the fluid.
            int shaftX = box.minX() + 1;
            int shaftZ = box.minZ() + 1;
            for (int y = box.minY() + 1; y <= box.maxY(); y++) {
                BlockPos rung = new BlockPos(shaftX, y, shaftZ);
                if (limit.isInside(rung)) {
                    level.setBlock(rung, SewerPalette.LADDER, Block.UPDATE_CLIENTS);
                }
            }
            // LIGHT THE CHAMBER. Three places claimed this room was lit - the palette javadoc, the test
            // comment and the spec - and none of it was true: the edit that placed it failed silently
            // and only the shaft and the dens got lamps. The chamber holds the ladder, the pool and the
            // barrels, and being unlit also left it spawnable, which is the opposite of what it is for.
            for (int dz : new int[]{2, -2}) {
                BlockPos hook = new BlockPos(box.getCenter().getX(), box.maxY() - 1,
                    box.getCenter().getZ() + dz);
                if (limit.isInside(hook)) {
                    level.setBlock(hook, SewerPalette.LIGHT, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * The way in (#90 phase 1): a shaft from the chamber up to daylight, capped by a manhole.
     *
     * <p><b>Built by the structure rather than placed separately</b>, which is the difference between a
     * manhole and a manhole that leads somewhere. The spec's phase 1 described scattering covers at
     * mineshaft density over a stub shaft; once the sewer itself existed, the honest version is that the
     * sewer brings its own entrance, so a cover a player finds is always a cover that opens onto
     * something. It also means the density question answers itself: one entrance per sewer.
     *
     * <p><b>Ladders, because the drop is the whole height of the rock.</b> Nothing else in the palette
     * is climbable, and a shaft you can fall down but not walk out of is a trap rather than a door.
     *
     * <p>The 3x3 of Reinforced Concrete is the surface marker the spec asks for - the pad is what makes
     * a one-block hole findable in a large biome without making the hole itself bigger.
     */
    public static class SewerEntrance extends SewerPiece {

        public SewerEntrance(int depth, BoundingBox box) {
            super(RCStructures.SEWER_ENTRANCE.get(), depth, box);
        }

        public SewerEntrance(CompoundTag tag) {
            super(RCStructures.SEWER_ENTRANCE.get(), tag);
        }

        /**
         * Absolute coordinates throughout, for the reason the root chamber uses them: this piece has no
         * orientation, and with {@code getOrientation()} null {@code getWorldX/Y/Z} hand back whatever
         * they are given. A shaft is the same in every direction, so there is nothing to orient.
         */
        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
            BoundingBox box = this.boundingBox;
            int x = box.minX() + 1;
            int z = box.minZ() + 1;
            for (int y = box.minY(); y <= box.maxY(); y++) {
                // Line the shaft so it reads as built rather than dug, and so it survives the rock
                // around it being anything at all.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dz != 0) {
                            put(level, limit, x + dx, y, z + dz, SewerPalette.WALL);
                        }
                    }
                }
                put(level, limit, x, y, z, SewerPalette.LADDER);
            }
            // The pad at the surface, and the cover in the middle of it.
            int top = box.maxY();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    put(level, limit, x + dx, top, z + dz, SewerPalette.PAD);
                }
            }
            put(level, limit, x, top, z, SewerPalette.COVER);
            // One at the foot, so the climb ends somewhere you can see. Nothing further up the shaft: a
            // lit column would read as maintained, and this system was abandoned.
            put(level, limit, x + 1, box.minY() + 2, z, SewerPalette.LIGHT);
        }

        /**
         * Write one block at an absolute position, bypassing {@code placeBlock}.
         *
         * <p><b>Because {@code placeBlock} mirrors the state, and {@code mirror} is null on a piece that
         * never called {@code setOrientation}.</b> Most blocks ignore mirroring entirely - a brick's
         * {@code mirror} returns itself and never touches the argument - so the root chamber has always
         * got away with it. A <b>ladder</b> does not: it rotates by {@code mirror.getRotation(facing)}
         * and throws on the null. The shaft is the first piece here to place a directional block, which
         * is why it is the first to find out.
         *
         * <p>Calling {@code setOrientation} would fix the null and break the coordinates - this piece
         * works in absolute positions, and an orientation switches {@code getWorldX/Z} into
         * transforming them. Writing directly keeps both correct, and matches how the chamber already
         * places its spawner.
         */
        private static void put(WorldGenLevel level, BoundingBox limit, int x, int y, int z,
                BlockState state) {
            BlockPos at = new BlockPos(x, y, z);
            if (limit.isInside(at)) {
                level.setBlock(at, state, Block.UPDATE_CLIENTS);
            }
        }
    }


}
