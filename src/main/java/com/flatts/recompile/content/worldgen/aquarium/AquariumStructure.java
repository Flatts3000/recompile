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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
     * <p><b>The feature's own position and nowhere else, and that is a correctness bound rather than a
     * frugality.</b> A structure lookup reads the chunk at {@code ChunkStatus.STRUCTURE_REFERENCES}, and
     * {@code WorldGenRegion.getChunk} allows a status that DEGRADES with distance from the chunk being
     * generated: {@code directDependencies().get(distance)}. Only distance zero is guaranteed to be far
     * enough along to answer. Probing further crashed real world generation with "Requested chunk
     * unavailable during world generation" the first time a tailings heap rolled next to an aquarium.
     *
     * <p><b>{@code hasChunk} is NOT the guard for this, which is what made the crash look impossible.</b>
     * It tests only {@code distance < directDependencies().size()} and says nothing about the status
     * that distance permits, so a probe can pass it and still throw inside {@code getChunk}. There is no
     * public accessor for the step's dependency list, so there is no distance-aware guard to write; the
     * bound is the origin.
     *
     * <p><b>What that gives up, and why little is lost.</b> A feature's origin is its minimum corner, so
     * a Building Husk originating up to sixteen blocks west or north of the footprint reaches into it
     * and is not caught here. The visible result is still handled, by the two mechanisms that do the
     * work: the building generates at {@code top_layer_modification}, AFTER every feature, so its shell
     * overwrites anything standing inside a room's box; and each room clears the column above its own
     * roof, so anything left overhead goes too. This check is the cheap first pass, not the guarantee.
     *
     * <p><b>A worldgen change of this shape cannot be proven by the GameTest harness</b>, which builds
     * pieces into a flat test plot and never runs a feature through real chunk generation. It took a
     * client boot to find, and it wants one again.
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
        return manager.getStructureAt(pos, s).isValid();
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

    /**
     * How far the guardian spawner may reach. One, because the empty {@code custom_spawn_rules} that
     * lets a guardian spawn at all also removes its water requirement, so the range is the only thing
     * left holding it in the tank.
     */
    public static final int GUARDIAN_SPAWN_RANGE = 1;

    /** The box a spawner at {@link #guardianSpawner} can actually place a mob in. */
    public static BoundingBox guardianSpawnReach(int ox, int base, int oz) {
        BlockPos at = guardianSpawner(ox, base, oz);
        int r = GUARDIAN_SPAWN_RANGE;
        // BaseSpawner: x/z are pos + (nextDouble - nextDouble) * range + 0.5, y is pos + nextInt(3) - 1.
        return bb(at.getX() - r, at.getY() - 1, at.getZ() - r, at.getX() + r, at.getY() + 1, at.getZ() + r);
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

    /**
     * Every VANILLA block this building places, declared once because a tool outside the game needs
     * to know and cannot find out for itself.
     *
     * <p><b>Why this exists.</b> {@code tools/resource_checklist} decides whether a vanilla resource
     * is reachable by closing over loot tables, recipes and a few declared interactions. It reads
     * structure NBT palettes to learn what a structure places - and this building has no NBT, because
     * it is procedural Java. So every vanilla block that exists in the world ONLY because this
     * building places it read as unreachable, and the generated checklist confidently said so: the
     * heart of the sea on the centrepiece pedestal, prismarine crystals off the sea lanterns, and
     * the sponges, all filed under "no ocean, monument, shipwreck or ocean ruin generates". That was
     * #366.
     *
     * <p><b>The earlier decision this reverses, and why.</b> Three moss entries were patched into the
     * Python by hand with a comment saying the wider gap would be "recorded in the pipeline's README
     * rather than patched here one block at a time". The README half was right and stays. The
     * objection was to unbounded hand-patching, and the answer to that is not to patch less but to
     * declare the whole set ONCE, here, next to the geometry it belongs to, and to guard it - which
     * is what {@code the_aquarium_places_exactly_the_vanilla_blocks_it_declares} does, in both
     * directions, so this list cannot silently gain or lose a member.
     *
     * <p><b>Vanilla only.</b> The building also places Leachate, Reinforced Concrete, Steel I-Beams
     * and a Display Pedestal; those are this mod's own and the checklist does not track them.
     */
    /**
     * The members of {@link #VANILLA_PLACED} whose placement is a function of ABSOLUTE position, so
     * a single building may legitimately contain none of them.
     *
     * <p>{@code AquariumPalette.mossy} is {@code hash(x, y, z) % 5 == 0} and {@code sparseMossy} is
     * {@code % 11 == 0}, both on world coordinates rather than on a seeded {@code RandomSource}. A
     * GameTest plot lands wherever the harness puts it, so whether any given building grows a moss
     * carpet depends on where it was built - and asserting one is present is a position-dependent
     * assertion, which is a flaky test wearing a guard's clothes. The manifest test caught exactly
     * that on itself: {@code pale_moss_carpet} was present on one plot and absent on the next.
     *
     * <p>They stay in {@link #VANILLA_PLACED} because the checklist's question is "can a player get
     * this here", and a block that appears in most buildings is a real source. What they are exempt
     * from is the must-be-present half of the guard. The must-not-be-undeclared half still covers
     * them, and that is the half that matters: it is the direction #366 failed in.
     */
    public static final Set<Block> VANILLA_PLACED_SPARSE = Set.of(
        Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET, Blocks.PALE_MOSS_BLOCK, Blocks.PALE_MOSS_CARPET,
        Blocks.PALE_HANGING_MOSS);

    public static final Set<Block> VANILLA_PLACED = Set.of(
        Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.SEA_LANTERN,
        Blocks.SPONGE, Blocks.WET_SPONGE,
        Blocks.GLASS, Blocks.TINTED_GLASS, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS,
        Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.IRON_BARS, Blocks.COBWEB,
        Blocks.SAND, Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL, Blocks.WATER,
        Blocks.CHEST,
        // The guardian and drowned spawners. Declared because the building genuinely places
        // it and this list means "what is placed", not "what can be carried away" - a
        // spawner drops nothing even to silk touch, so the checklist credits its empty loot
        // table and nothing changes. Leaving it out would make the guard lie by omission.
        Blocks.SPAWNER,
        Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET, Blocks.PALE_MOSS_BLOCK, Blocks.PALE_MOSS_CARPET,
        Blocks.PALE_HANGING_MOSS,
        Blocks.DEAD_TUBE_CORAL, Blocks.DEAD_TUBE_CORAL_FAN, Blocks.DEAD_TUBE_CORAL_BLOCK,
        Blocks.DEAD_BRAIN_CORAL, Blocks.DEAD_BRAIN_CORAL_FAN, Blocks.DEAD_BRAIN_CORAL_BLOCK,
        Blocks.DEAD_BUBBLE_CORAL, Blocks.DEAD_BUBBLE_CORAL_FAN, Blocks.DEAD_BUBBLE_CORAL_BLOCK,
        Blocks.DEAD_FIRE_CORAL, Blocks.DEAD_FIRE_CORAL_FAN, Blocks.DEAD_FIRE_CORAL_BLOCK,
        Blocks.DEAD_HORN_CORAL, Blocks.DEAD_HORN_CORAL_FAN, Blocks.DEAD_HORN_CORAL_BLOCK);

    /**
     * Vanilla ITEMS this building puts into the world inside a block entity, as opposed to placing
     * as a block.
     *
     * <p>Separate from {@link #VANILLA_PLACED} because a block sweep cannot see them and because
     * they are reached differently: you break the pedestal and pick the item up. The curator chest
     * is NOT here - its contents are an ordinary loot table, which the checklist already reads.
     */
    public static final Set<Item> VANILLA_ITEMS_PLACED = Set.of(Items.HEART_OF_THE_SEA);

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
