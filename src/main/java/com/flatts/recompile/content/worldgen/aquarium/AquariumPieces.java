package com.flatts.recompile.content.worldgen.aquarium;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.DisplayPedestalBlockEntity;
import com.flatts.recompile.content.worldgen.aquarium.AquariumStructure.Door;
import com.flatts.recompile.content.worldgen.aquarium.AquariumStructure.Room;
import com.flatts.recompile.content.worldgen.tower.Spawners;
import com.flatts.recompile.registry.RCStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The rooms of the Municipal Aquarium. One piece type per room, the sewer's shape: an abstract room
 * owns the shell, the floor, the door cutting and the dressing that every room shares, and each of the
 * seven concrete rooms supplies only what makes it itself.
 *
 * <p><b>Every number is derived from the box.</b> A piece serialises its bounding box and nothing else,
 * so the origin the building was laid out from is recovered with {@link Room#originOf} and every
 * feature is recomputed from it. A field that was not written would regenerate wrong on reload, and
 * nothing would say so until a player walked into a room with no door.
 *
 * <p><b>Pieces are un-oriented and work in absolute coordinates.</b> That is the convention the sewer's
 * dens use; the oriented convention's local/world axis swap has cost a rewrite once already and this
 * building has a fixed facing, so there is nothing for orientation to buy.
 *
 * <p><b>Fluids are placed as a source in every cell of a volume that is bounded on every side at every
 * fluid level.</b> {@code placeBlock} schedules a fluid tick for each fluid it writes, so a source with
 * an air neighbour drains; the sump learned that the hard way. The guardian tank's breach is above its
 * waterline for exactly this reason, and the layout test holds it there.
 */
public final class AquariumPieces {

    static final ResourceKey<LootTable> CHEST_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "chests/aquarium_curator"));
    static final ResourceKey<LootTable> SILT_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "archaeology/aquarium_silt"));

    private AquariumPieces() {
    }

    public static StructurePiece pieceFor(Room room, BoundingBox box) {
        return switch (room) {
            case FORECOURT -> new Forecourt(box);
            case LOBBY -> new Lobby(box);
            case GALLERY -> new Gallery(box);
            case BIG_TANK -> new BigTank(box);
            case GUARDIAN_TANK -> new GuardianTank(box);
            case FILTRATION_HALL -> new FiltrationHall(box);
            case BACK_OF_HOUSE -> new BackOfHouse(box);
        };
    }

    // ------------------------------------------------------------------ the shared room

    public abstract static class AquariumRoom extends StructurePiece {

        protected AquariumRoom(StructurePieceType type, BoundingBox box) {
            super(type, 0, box);
            // Explicitly un-oriented. The box constructor leaves mirror and rotation NULL until
            // setOrientation runs, and placeBlock dereferences both; null orientation is what makes
            // getWorldX/Y/Z return their argument untouched, which is the absolute-coordinate contract.
            this.setOrientation(null);
        }

        protected AquariumRoom(StructurePieceType type, CompoundTag tag) {
            super(type, tag);
            this.setOrientation(null);
        }

        public abstract Room room();

        /** What this room's own (unshared) wall cells are made of. */
        protected BlockState wall(int x, int y, int z, int ox, int base, int oz) {
            return AquariumPalette.weathered(AquariumPalette.WALL, AquariumPalette.AGED_WALL,
                AquariumPalette.WET_WALL, x, y, z);
        }

        /** What a wall cell shared with {@code other} is made of. Both rooms must answer the same. */
        protected static BlockState wallBetween(Room a, Room b, int x, int y, int z, int base) {
            Room lo = a.ordinal() < b.ordinal() ? a : b;
            Room hi = a.ordinal() < b.ordinal() ? b : a;
            if (lo == Room.GALLERY && hi == Room.BIG_TANK) {
                // The tank wall the gallery looks through: prismarine at the foot, glass above, cracked.
                if (y <= base + 1) {
                    return AquariumPalette.CLADDING;
                }
                return y >= base + 3 && AquariumPalette.cracked(x, y, z)
                    ? AquariumPalette.HOLLOW : AquariumPalette.GLASS;
            }
            if (lo == Room.GALLERY && hi == Room.GUARDIAN_TANK) {
                return y <= base ? AquariumPalette.SHELL : AquariumPalette.TANK_GLASS;
            }
            return AquariumPalette.SHELL;
        }

        /** What this room dresses its interior with, after the shell and the doors. */
        protected abstract void dress(WorldGenLevel level, BoundingBox limit, RandomSource random,
                int ox, int base, int oz);

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            // Nothing. The box is the whole state.
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox limit, ChunkPos chunk, BlockPos origin) {
            BlockPos o = room().originOf(this.boundingBox);
            int ox = o.getX();
            int base = o.getY();
            int oz = o.getZ();
            BoundingBox b = this.boundingBox;

            // 1. The shell. Floor is a double slab; the faces are walls; the top is a roof or open sky.
            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    this.placeBlock(level, AquariumPalette.SHELL, x, b.minY(), z, limit);
                    this.placeBlock(level, floor(x, z, ox, base, oz), x, b.minY() + 1, z, limit);
                    boolean face = x == b.minX() || x == b.maxX() || z == b.minZ() || z == b.maxZ();
                    for (int y = b.minY() + 2; y <= b.maxY(); y++) {
                        BlockState state;
                        if (y == b.maxY() && room().roofed()) {
                            state = roof(x, y, z, ox, base, oz);
                        } else if (face) {
                            Room other = AquariumStructure.otherRoomAt(room(), new BlockPos(x, y, z), ox, base, oz);
                            state = other == null ? wall(x, y, z, ox, base, oz)
                                : wallBetween(room(), other, x, y, z, base);
                        } else {
                            state = AquariumPalette.HOLLOW;
                        }
                        this.placeBlock(level, state, x, y, z, limit);
                    }
                }
            }

            // 2. Every doorway on one of this room's planes, cut by this room too, so the cut survives
            //    whichever piece the chunk order writes last.
            for (Door door : AquariumStructure.doors(ox, base, oz)) {
                if (!door.joins(room())) {
                    continue;
                }
                BoundingBox c = door.cells();
                for (int x = c.minX(); x <= c.maxX(); x++) {
                    for (int y = c.minY(); y <= c.maxY(); y++) {
                        for (int z = c.minZ(); z <= c.maxZ(); z++) {
                            this.placeBlock(level, AquariumPalette.HOLLOW, x, y, z, limit);
                        }
                    }
                }
            }

            // 3. What makes this room itself.
            dress(level, limit, random, ox, base, oz);
        }

        protected BlockState floor(int x, int z, int ox, int base, int oz) {
            return AquariumPalette.FLOOR;
        }

        protected BlockState roof(int x, int y, int z, int ox, int base, int oz) {
            return AquariumPalette.SHELL;
        }

        /** A fluid source in every cell of a box. The caller guarantees the box is bounded. */
        protected void fill(WorldGenLevel level, BoundingBox limit, BoundingBox cells, BlockState state) {
            for (int x = cells.minX(); x <= cells.maxX(); x++) {
                for (int y = cells.minY(); y <= cells.maxY(); y++) {
                    for (int z = cells.minZ(); z <= cells.maxZ(); z++) {
                        this.placeBlock(level, state, x, y, z, limit);
                    }
                }
            }
        }

        /**
         * A brushable and its loot table, together, or not at all. A brushable with no table brushes
         * away to nothing, silently, which is worse than the gravel it replaced (the sewer's rule).
         */
        protected void deposit(WorldGenLevel level, BoundingBox limit, BlockState state,
                int x, int y, int z, RandomSource random) {
            this.placeBlock(level, state, x, y, z, limit);
            BlockPos at = new BlockPos(x, y, z);
            if (limit.isInside(at) && level.getBlockEntity(at) instanceof BrushableBlockEntity brushable) {
                brushable.setLootTable(SILT_LOOT, random.nextLong());
            }
        }

        protected void cobwebs(WorldGenLevel level, BoundingBox limit, RandomSource random, BoundingBox interior) {
            for (int x = interior.minX(); x <= interior.maxX(); x++) {
                for (int z = interior.minZ(); z <= interior.maxZ(); z++) {
                    if (random.nextInt(24) == 0) {
                        this.placeBlock(level, AquariumPalette.AGE, x, interior.maxY(), z, limit);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the seven rooms

    /** Open to the sky, railed, the collapsed frontage that is the way in. */
    public static class Forecourt extends AquariumRoom {
        public Forecourt(BoundingBox box) {
            super(RCStructures.AQUARIUM_FORECOURT.get(), box);
        }

        public Forecourt(CompoundTag tag) {
            super(RCStructures.AQUARIUM_FORECOURT.get(), tag);
        }

        @Override
        public Room room() {
            return Room.FORECOURT;
        }

        /** The forecourt has no walls: its faces are railings at knee height and air above. */
        @Override
        protected BlockState wall(int x, int y, int z, int ox, int base, int oz) {
            boolean street = z == oz + 17 && x >= ox - 1 && x <= ox;   // the gap onto the yard
            return y == base + 1 && !street ? AquariumPalette.RAIL : AquariumPalette.HOLLOW;
        }

        @Override
        protected void dress(WorldGenLevel level, BoundingBox limit, RandomSource random,
                int ox, int base, int oz) {
            // Two portico posts, the steel that held a canopy that is long gone.
            for (int x : new int[]{ox - 6, ox + 5}) {
                for (int y = base + 1; y <= base + 3; y++) {
                    this.placeBlock(level, AquariumPalette.BEAM_Y, x, y, oz + 17, limit);
                }
            }
        }
    }

    /** Ticket desk, puddles, cobwebs. */
    public static class Lobby extends AquariumRoom {
        public Lobby(BoundingBox box) {
            super(RCStructures.AQUARIUM_LOBBY.get(), box);
        }

        public Lobby(CompoundTag tag) {
            super(RCStructures.AQUARIUM_LOBBY.get(), tag);
        }

        @Override
        public Room room() {
            return Room.LOBBY;
        }

        @Override
        protected BlockState roof(int x, int y, int z, int ox, int base, int oz) {
            return x == ox && z == oz + 8 ? AquariumPalette.LIGHT : AquariumPalette.SHELL;
        }

        @Override
        protected void dress(WorldGenLevel level, BoundingBox limit, RandomSource random,
                int ox, int base, int oz) {
            for (int x = ox - 1; x <= ox + 1; x++) {
                this.placeBlock(level, AquariumPalette.WALL, x, base + 1, oz + 9, limit);
            }
            for (BlockPos p : new BlockPos[]{new BlockPos(ox - 3, base, oz + 7),
                    new BlockPos(ox + 2, base, oz + 9), new BlockPos(ox, base, oz + 11)}) {
                this.placeBlock(level, AquariumPalette.FLUID, p.getX(), p.getY(), p.getZ(), limit);
            }
            cobwebs(level, limit, random, room().interior(ox, base, oz));
        }
    }

    /** The hub: tank rows with leachate in their floors and the dead coral on the kerbs. */
    public static class Gallery extends AquariumRoom {
        private static final BlockState[] DEAD_CORAL = {
            Blocks.DEAD_TUBE_CORAL.defaultBlockState(), Blocks.DEAD_BRAIN_CORAL_FAN.defaultBlockState(),
            Blocks.DEAD_BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_FIRE_CORAL.defaultBlockState(),
            Blocks.DEAD_HORN_CORAL_FAN.defaultBlockState(), Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState(),
            Blocks.DEAD_BRAIN_CORAL.defaultBlockState(), Blocks.DEAD_BUBBLE_CORAL_FAN.defaultBlockState(),
            Blocks.DEAD_FIRE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_HORN_CORAL.defaultBlockState(),
            Blocks.DEAD_TUBE_CORAL_FAN.defaultBlockState(), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState(),
            Blocks.DEAD_BUBBLE_CORAL.defaultBlockState(), Blocks.DEAD_FIRE_CORAL_FAN.defaultBlockState(),
            Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState()};

        public Gallery(BoundingBox box) {
            super(RCStructures.AQUARIUM_GALLERY.get(), box);
        }

        public Gallery(CompoundTag tag) {
            super(RCStructures.AQUARIUM_GALLERY.get(), tag);
        }

        @Override
        public Room room() {
            return Room.GALLERY;
        }

        /** Cladding: prismarine to shoulder height, a dark band, concrete above. */
        @Override
        protected BlockState wall(int x, int y, int z, int ox, int base, int oz) {
            if (y <= base + 3) {
                return AquariumPalette.CLADDING;
            }
            return y == base + 4 ? AquariumPalette.CLADDING_BAND : AquariumPalette.SHELL;
        }

        @Override
        protected BlockState roof(int x, int y, int z, int ox, int base, int oz) {
            return z == oz && (x - ox) % 4 == 0 ? AquariumPalette.LIGHT : AquariumPalette.SHELL;
        }

        @Override
        protected void dress(WorldGenLevel level, BoundingBox limit, RandomSource random,
                int ox, int base, int oz) {
            // The kerbs, then the troughs cut into the floor beside them, then the coral on the kerbs.
            for (int x = ox - 9; x <= ox + 8; x++) {
                this.placeBlock(level, AquariumPalette.CLADDING, x, base, oz - 3, limit);
                this.placeBlock(level, AquariumPalette.CLADDING, x, base, oz + 2, limit);
            }
            for (BoundingBox trough : AquariumStructure.troughs(ox, base, oz)) {
                fill(level, limit, trough, AquariumPalette.FLUID);
            }
            int i = 0;
            for (int x = ox - 9; x <= ox + 8; x += 2) {
                this.placeBlock(level, DEAD_CORAL[i++ % DEAD_CORAL.length], x, base + 1, oz - 3, limit);
                this.placeBlock(level, DEAD_CORAL[i++ % DEAD_CORAL.length], x + 1, base + 1, oz + 2, limit);
            }
            // The roof frame, and the viewing steps up to the guardian tank's breach.
            for (int x : new int[]{ox - 5, ox, ox + 5}) {
                for (int z = oz - 4; z <= oz + 3; z++) {
                    this.placeBlock(level, AquariumPalette.BEAM_Z, x, base + 6, z, limit);
                }
            }
            for (int step = 0; step < 3; step++) {
                for (int z = oz - 1; z <= oz; z++) {
                    for (int y = base + 1; y <= base + 1 + step; y++) {
                        this.placeBlock(level, AquariumPalette.CLADDING_PLAIN, ox + 6 + step, y, z, limit);
                    }
                }
            }
            cobwebs(level, limit, random, room().interior(ox, base, oz));
        }
    }

    /** The centrepiece: the tallest volume, glass on three sides, the heart of the sea on its plinth. */
    public static class BigTank extends AquariumRoom {
        public BigTank(BoundingBox box) {
            super(RCStructures.AQUARIUM_BIG_TANK.get(), box);
        }

        public BigTank(CompoundTag tag) {
            super(RCStructures.AQUARIUM_BIG_TANK.get(), tag);
        }

        @Override
        public Room room() {
            return Room.BIG_TANK;
        }

        @Override
        protected BlockState wall(int x, int y, int z, int ox, int base, int oz) {
            BoundingBox b = room().box(ox, base, oz);
            boolean corner = (x == b.minX() || x == b.maxX()) && (z == b.minZ() || z == b.maxZ());
            if (corner) {
                return AquariumPalette.SHELL;
            }
            if (y <= base + 1) {
                return AquariumPalette.CLADDING;
            }
            return y >= base + 3 && AquariumPalette.cracked(x, y, z)
                ? AquariumPalette.HOLLOW : AquariumPalette.GLASS;
        }

        @Override
        protected BlockState roof(int x, int y, int z, int ox, int base, int oz) {
            return z == oz - 8 && (x == ox - 1 || x == ox) ? AquariumPalette.LIGHT : AquariumPalette.SHELL;
        }

        @Override
        protected void dress(WorldGenLevel level, BoundingBox limit, RandomSource random,
                int ox, int base, int oz) {
            BlockPos at = AquariumStructure.pedestal(ox, base, oz);
            this.placeBlock(level, AquariumPalette.PEDESTAL, at.getX(), at.getY(), at.getZ(), limit);
            if (limit.isInside(at) && level.getBlockEntity(at) instanceof DisplayPedestalBlockEntity pedestal) {
                pedestal.setDisplayed(new ItemStack(Items.HEART_OF_THE_SEA));
            }
        }
    }

    /** The one wet tank: tinted glass, real water, a guardian, and a breach above the waterline. */
    public static class GuardianTank extends AquariumRoom {
        public GuardianTank(BoundingBox box) {
            super(RCStructures.AQUARIUM_GUARDIAN_TANK.get(), box);
        }

        public GuardianTank(CompoundTag tag) {
            super(RCStructures.AQUARIUM_GUARDIAN_TANK.get(), tag);
        }

        @Override
        public Room room() {
            return Room.GUARDIAN_TANK;
        }

        @Override
        protected BlockState wall(int x, int y, int z, int ox, int base, int oz) {
            return y <= base ? AquariumPalette.SHELL : AquariumPalette.TANK_GLASS;
        }

        @Override
        protected BlockState roof(int x, int y, int z, int ox, int base, int oz) {
            return x == ox + 12 && z == oz - 1 ? AquariumPalette.LIGHT : AquariumPalette.SHELL;
        }

        @Override
        protected void dress(WorldGenLevel level, BoundingBox limit, RandomSource random,
                int ox, int base, int oz) {
            // Water in every cell, and then the spawner replaces one of them. Spawners.place writes an
            // empty custom_spawn_rules, which is what lets a guardian spawn at all: Guardian's own
            // predicate demands #minecraft:water BELOW with no spawner exemption, and that is bypassed
            // wholesale. The water is still required - for isInWater, for its navigation, and to keep
            // RCLeachateContact from drowning it - so the reason shifts from spawning to living.
            fill(level, limit, AquariumStructure.guardianWater(ox, base, oz), AquariumPalette.TANK_WATER);
            Spawners.place(level, limit, AquariumStructure.guardianSpawner(ox, base, oz),
                "minecraft:guardian", 4, null);
        }
    }

    /** Half-sunk, silted, sponges on the wall, the sump at the bottom, a ramp down from the gallery. */
    public static class FiltrationHall extends AquariumRoom {
        public FiltrationHall(BoundingBox box) {
            super(RCStructures.AQUARIUM_FILTRATION_HALL.get(), box);
        }

        public FiltrationHall(CompoundTag tag) {
            super(RCStructures.AQUARIUM_FILTRATION_HALL.get(), tag);
        }

        @Override
        public Room room() {
            return Room.FILTRATION_HALL;
        }

        @Override
        protected BlockState wall(int x, int y, int z, int ox, int base, int oz) {
            return AquariumPalette.weathered(AquariumPalette.WET_WALL, AquariumPalette.AGED_WALL,
                AquariumPalette.WALL, x, y, z);
        }

        @Override
        protected BlockState roof(int x, int y, int z, int ox, int base, int oz) {
            return x == ox - 16 && z == oz ? AquariumPalette.LIGHT : AquariumPalette.SHELL;
        }

        @Override
        protected void dress(WorldGenLevel level, BoundingBox limit, RandomSource random,
                int ox, int base, int oz) {
            // The ramp: four full-block steps down from the gallery door, one course each.
            for (int step = 0; step < 4; step++) {
                for (int z = oz - 1; z <= oz + 1; z++) {
                    this.placeBlock(level, AquariumPalette.FLOOR, ox - 11 - step, base - step, z, limit);
                }
            }
            // The sump, cut into the upper floor course, bounded by floor around and slab below.
            fill(level, limit, AquariumStructure.sump(ox, base, oz), AquariumPalette.FLUID);
            // The silt bed, each cell carrying its own table.
            BoundingBox silt = AquariumStructure.silt(ox, base, oz);
            for (int x = silt.minX(); x <= silt.maxX(); x++) {
                for (int z = silt.minZ(); z <= silt.maxZ(); z++) {
                    BlockState s = (x + z) % 3 == 0 ? AquariumPalette.FINE_SILT : AquariumPalette.SILT;
                    deposit(level, limit, s, x, silt.minY(), z, random);
                }
            }
            // The filter bank on the north wall.
            for (int x = ox - 20; x <= ox - 14; x += 2) {
                for (int y = base - 3; y <= base - 1; y++) {
                    BlockState s = (x + y) % 2 == 0 ? AquariumPalette.SPONGE : AquariumPalette.WET_SPONGE;
                    this.placeBlock(level, s, x, y, oz - 4, limit);
                }
            }
            cobwebs(level, limit, random, room().interior(ox, base, oz));
        }
    }

    /** The curator's chest and the drowned that got in. */
    public static class BackOfHouse extends AquariumRoom {
        public BackOfHouse(BoundingBox box) {
            super(RCStructures.AQUARIUM_BACK_OF_HOUSE.get(), box);
        }

        public BackOfHouse(CompoundTag tag) {
            super(RCStructures.AQUARIUM_BACK_OF_HOUSE.get(), tag);
        }

        @Override
        public Room room() {
            return Room.BACK_OF_HOUSE;
        }

        @Override
        protected BlockState roof(int x, int y, int z, int ox, int base, int oz) {
            return x == ox - 10 && z == oz + 6 ? AquariumPalette.LIGHT : AquariumPalette.SHELL;
        }

        @Override
        protected void dress(WorldGenLevel level, BoundingBox limit, RandomSource random,
                int ox, int base, int oz) {
            BlockPos chest = AquariumStructure.chest(ox, base, oz);
            if (limit.isInside(chest)) {
                level.setBlock(chest, Blocks.CHEST.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_CLIENTS);
                RandomizableContainer.setBlockEntityLootTable(level, random, chest, CHEST_LOOT);
            }
            Spawners.place(level, limit, AquariumStructure.drownedSpawner(ox, base, oz),
                "minecraft:drowned", 4, null);
            cobwebs(level, limit, random, room().interior(ox, base, oz));
        }
    }
}
