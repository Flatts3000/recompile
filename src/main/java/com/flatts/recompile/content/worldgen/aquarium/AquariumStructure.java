package com.flatts.recompile.content.worldgen.aquarium;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCStructures;
import com.mojang.serialization.MapCodec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

/**
 * The Municipal Aquarium: a drained public aquarium in the demolition yard, the answer to #324
 * (spec {@code docs/municipal_aquarium_spec.md}).
 *
 * <p><b>It is the sewer's shape sited like the tower.</b> Both landmark precedents are solids of
 * revolution built as one piece from a radius-by-height function; this is seven connected rooms, so
 * the model is {@code SewerStructure} - named pieces with computed boxes - while the siting is the
 * cooling tower's, {@code onTopOfChunkCenter} on the world-surface heightmap.
 *
 * <p><b>The layout lives here, once, as pure arithmetic.</b> {@link Room#box} is what generation
 * places, what every piece derives its own geometry from, what the JUnit layout test measures and what
 * the GameTests build. That is the {@code SewerFixtures} lesson applied on day one rather than after
 * the second drift: tests ask this class for its boxes and never retype them. A drawing of the plan in
 * the spec would be a second copy of this geometry, and this repo has evidence a second copy drifts.
 *
 * <p>The building always faces the same way (forecourt on +Z). Neither shipped landmark had an
 * orientation to speak of, and a fixed one is what lets every piece be un-oriented and work in absolute
 * coordinates - which is the coordinate convention the sewer's dens use and the one whose local/world
 * axis swap has never bitten.
 */
public class AquariumStructure extends Structure {

    public static final MapCodec<AquariumStructure> CODEC = simpleCodec(AquariumStructure::new);

    public static final ResourceKey<Structure> KEY = ResourceKey.create(Registries.STRUCTURE,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "municipal_aquarium"));

    /**
     * How far above its own roof each room clears. Structures at top_layer_modification write after
     * every feature, so anything a feature left standing INSIDE a room's box is overwritten by the
     * shell - but a Building Husk's lattice or a steel stack rising above an open forecourt is outside
     * every box and would stand straight through the building. Husks are tall; this is generous.
     */
    public static final int CLEAR_ABOVE = 28;

    /**
     * Whether this structure has claimed the ground around {@code pos}, for the yard's tall features to
     * ask before they place. A structure start's box is known at the structure_starts stage, which is
     * before any feature runs, so a feature can decline to put a husk where a room is about to be.
     *
     * <p>Checks a ring rather than the one point, because a husk's origin is its centre and its frame
     * reaches well past it; a husk centred just outside the footprint still leans over it.
     */
    public static boolean claims(WorldGenLevel level, BlockPos pos) {
        // A WorldGenLevel carries no structure manager of its own in 26.1. Vanilla's decoration
        // binds the server's manager to the region it is generating (forWorldGenRegion), which is
        // what makes chunk access safe from a worldgen thread; anything else is a feature asking
        // the live level about a chunk it is in the middle of making.
        if (!(level instanceof WorldGenRegion region)) {
            return false;
        }
        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(KEY);
        if (structure.isEmpty()) {
            return false;
        }
        Structure s = structure.get().value();
        StructureManager manager = region.getLevel().structureManager().forWorldGenRegion(region);
        for (BlockPos probe : new BlockPos[]{pos, pos.offset(10, 0, 0), pos.offset(-10, 0, 0),
                pos.offset(0, 0, 10), pos.offset(0, 0, -10)}) {
            if (manager.getStructureAt(probe, s).isValid()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The seven rooms, each with its box as offsets from the origin (ox, base, oz) where {@code base}
     * is the ground block the building stands on. Every room's box begins at {@code base - 1} so the
     * floor is a double slab and a puddle cut into the upper course is bounded below; the Filtration
     * Hall is half-sunk and begins four courses lower.
     *
     * <p>Rooms share wall PLANES, not walls: a room's max face is its neighbour's min face, so both
     * pieces draw the same cells and both cut the same doors, and the order chunks generate in cannot
     * matter. {@link #wallBetween} decides what a shared plane is made of, for the same reason.
     */
    public enum Room {
        FORECOURT(-6, -1, 12, 5, 3, 17, false),
        LOBBY(-5, -1, 4, 4, 6, 12, true),
        GALLERY(-10, -1, -5, 9, 7, 4, true),
        BIG_TANK(-4, -1, -12, 3, 10, -5, true),
        GUARDIAN_TANK(9, -1, -3, 15, 7, 2, true),
        FILTRATION_HALL(-22, -5, -5, -10, 4, 4, true),
        BACK_OF_HOUSE(-14, -1, 4, -6, 5, 9, true);

        private final int dx0;
        private final int dy0;
        private final int dz0;
        private final int dx1;
        private final int dy1;
        private final int dz1;
        private final boolean roofed;

        Room(int dx0, int dy0, int dz0, int dx1, int dy1, int dz1, boolean roofed) {
            this.dx0 = dx0;
            this.dy0 = dy0;
            this.dz0 = dz0;
            this.dx1 = dx1;
            this.dy1 = dy1;
            this.dz1 = dz1;
            this.roofed = roofed;
        }

        public BoundingBox box(int ox, int base, int oz) {
            return new BoundingBox(ox + dx0, base + dy0, oz + dz0, ox + dx1, base + dy1, oz + dz1);
        }

        /** The origin this room's box was built from; the inverse of {@link #box}. */
        public BlockPos originOf(BoundingBox box) {
            return new BlockPos(box.minX() - dx0, box.minY() - dy0, box.minZ() - dz0);
        }

        /** The box shrunk by one on every side: the air a player stands in. */
        public BoundingBox interior(int ox, int base, int oz) {
            BoundingBox b = box(ox, base, oz);
            return new BoundingBox(b.minX() + 1, b.minY() + 2, b.minZ() + 1,
                b.maxX() - 1, roofed ? b.maxY() - 1 : b.maxY(), b.maxZ() - 1);
        }

        public boolean roofed() {
            return roofed;
        }
    }

    /** A doorway: the cells cut out of the plane two rooms share. */
    public record Door(Room a, Room b, BoundingBox cells) {
        public boolean joins(Room room) {
            return room == a || room == b;
        }
    }

    /**
     * Every doorway in the building. Each lies on a plane exactly two rooms share, which the layout
     * test asserts; each is cut by both rooms, which is what makes the cut survive whichever piece
     * writes last.
     */
    public static List<Door> doors(int ox, int base, int oz) {
        return List.of(
            // The collapsed frontage: three tall, so it reads as a wall that came down rather than a door.
            new Door(Room.FORECOURT, Room.LOBBY, bb(ox - 2, base + 1, oz + 12, ox + 1, base + 3, oz + 12)),
            new Door(Room.LOBBY, Room.GALLERY, bb(ox - 1, base + 1, oz + 4, ox, base + 2, oz + 4)),
            // The breach into the centrepiece tank.
            new Door(Room.GALLERY, Room.BIG_TANK, bb(ox - 1, base + 1, oz - 5, ox, base + 3, oz - 5)),
            // ABOVE THE WATERLINE, or the tank empties into the gallery (rule 2 of the room graph).
            new Door(Room.GALLERY, Room.GUARDIAN_TANK, bb(ox + 9, base + 4, oz - 1, ox + 9, base + 5, oz)),
            new Door(Room.GALLERY, Room.FILTRATION_HALL, bb(ox - 10, base + 1, oz - 1, ox - 10, base + 2, oz + 1)),
            new Door(Room.GALLERY, Room.BACK_OF_HOUSE, bb(ox - 8, base + 1, oz + 4, ox - 8, base + 2, oz + 4)),
            new Door(Room.FILTRATION_HALL, Room.BACK_OF_HOUSE, bb(ox - 12, base + 1, oz + 4, ox - 12, base + 2, oz + 4)));
    }

    /** The guardian tank's water: the only water in the building, three deep, and bounded on every side. */
    public static BoundingBox guardianWater(int ox, int base, int oz) {
        return bb(ox + 10, base + 1, oz - 2, ox + 14, base + 3, oz + 1);
    }

    /** The sump: leachate cut into the hall's upper floor course, the lowest point in the structure. */
    public static BoundingBox sump(int ox, int base, int oz) {
        return bb(ox - 21, base - 4, oz - 1, ox - 17, base - 4, oz + 1);
    }

    /** The brushable silt bed in the hall, where the sherds are. */
    public static BoundingBox silt(int ox, int base, int oz) {
        return bb(ox - 15, base - 4, oz - 3, ox - 12, base - 4, oz + 2);
    }

    /**
     * The two exhibit bays along the gallery's long walls: one deep, glass-fronted, their floor in the
     * upper floor course. Every other cell of a bay floor is leachate and the rest is sand with dead
     * coral standing on it, so the tank rows read as tanks rather than as planters - the first build
     * put the coral out on a kerb by itself, and the owner called it weird, correctly.
     */
    public static List<BoundingBox> bays(int ox, int base, int oz) {
        return List.of(
            bb(ox - 9, base, oz - 4, ox + 8, base, oz - 4),
            bb(ox - 9, base, oz + 3, ox + 8, base, oz + 3));
    }

    /** Where the heart of the sea sits. */
    public static BlockPos pedestal(int ox, int base, int oz) {
        return new BlockPos(ox, base + 1, oz - 8);
    }

    public static BlockPos guardianSpawner(int ox, int base, int oz) {
        return new BlockPos(ox + 12, base + 1, oz - 1);
    }

    public static BlockPos drownedSpawner(int ox, int base, int oz) {
        return new BlockPos(ox - 9, base + 1, oz + 7);
    }

    public static BlockPos chest(int ox, int base, int oz) {
        return new BlockPos(ox - 13, base + 1, oz + 8);
    }

    /**
     * What a wall cell two rooms share is made of. A glass tank wall that the gallery drew as concrete
     * would be whichever chunk generated last, so the answer has to be a function of the pair.
     */
    public static Room otherRoomAt(Room self, BlockPos cell, int ox, int base, int oz) {
        for (Room room : Room.values()) {
            if (room != self && room.box(ox, base, oz).isInside(cell)) {
                return room;
            }
        }
        return null;
    }

    /** The rooms a walk from {@code start} through the doors can reach. This is rule 1 of the room graph. */
    public static Set<Room> reachableFrom(Room start, int ox, int base, int oz) {
        List<Door> doors = doors(ox, base, oz);
        Set<Room> seen = EnumSet.of(start);
        Deque<Room> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty()) {
            Room here = queue.poll();
            for (Door door : doors) {
                if (!door.joins(here)) {
                    continue;
                }
                Room there = door.a() == here ? door.b() : door.a();
                if (seen.add(there)) {
                    queue.add(there);
                }
            }
        }
        return seen;
    }

    /** The union of every room's box, for tests that sweep the whole building. */
    public static BoundingBox footprint(int ox, int base, int oz) {
        BoundingBox all = null;
        for (Room room : Room.values()) {
            BoundingBox b = room.box(ox, base, oz);
            all = all == null ? b : BoundingBox.fromCorners(
                new BlockPos(Math.min(all.minX(), b.minX()), Math.min(all.minY(), b.minY()), Math.min(all.minZ(), b.minZ())),
                new BlockPos(Math.max(all.maxX(), b.maxX()), Math.max(all.maxY(), b.maxY()), Math.max(all.maxZ(), b.maxZ())));
        }
        return all;
    }

    /** One piece per room, which is what generation adds and what the GameTests build. */
    public static List<StructurePiece> pieces(int ox, int base, int oz) {
        List<StructurePiece> out = new ArrayList<>();
        for (Room room : Room.values()) {
            out.add(AquariumPieces.pieceFor(room, room.box(ox, base, oz)));
        }
        return out;
    }

    static BoundingBox bb(int x0, int y0, int z0, int x1, int y1, int z1) {
        return new BoundingBox(x0, y0, z0, x1, y1, z1);
    }

    public AquariumStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public java.util.Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        // WORLD_SURFACE_WG for the tower's reason: the building stands on whatever the generator put
        // down, and this world's surface is a flat coarse-dirt cap.
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder -> {
            int x = context.chunkPos().getBlockX(8);
            int z = context.chunkPos().getBlockZ(8);
            int base = context.chunkGenerator().getFirstOccupiedHeight(
                x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            for (StructurePiece piece : pieces(x, base, z)) {
                builder.addPiece(piece);
            }
        });
    }

    @Override
    public StructureType<?> type() {
        return RCStructures.MUNICIPAL_AQUARIUM.get();
    }
}
