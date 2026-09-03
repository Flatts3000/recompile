package com.flatts.recompile.content.worldgen.aquarium;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.worldgen.aquarium.AquariumStructure.Door;
import com.flatts.recompile.content.worldgen.aquarium.AquariumStructure.Room;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The room graph, measured with no world. This is the aquarium's equivalent of
 * {@code CoolingTowerProfileTest}: the tower's shape was one function of height so its test evaluated
 * that function; this building's shape is rooms and doors, so its test walks them.
 *
 * <p>Every number here comes from {@link AquariumStructure}'s statics, never from a copy of them.
 * That is the {@code SewerFixtures} lesson: a test that retypes a room's size measures a room the game
 * may have stopped generating, and goes on passing while the real one breaks.
 */
class AquariumLayoutTest {

    /** An arbitrary origin; the layout is pure arithmetic and must not care. */
    private static final int OX = 1000;
    private static final int BASE = 64;
    private static final int OZ = -2000;

    @Test
    @DisplayName("every room is reachable from the forecourt without breaking a block")
    void everyRoomIsReachableFromTheForecourt() {
        Set<Room> reached = AquariumStructure.reachableFrom(Room.FORECOURT, OX, BASE, OZ);
        assertEquals(EnumSet.allOf(Room.class), reached,
            "rule 1 of the room graph: a room the doors do not reach is a room the player has to break "
                + "into, and this building is meant to open itself");
    }

    @Test
    @DisplayName("every doorway lies on a plane shared by exactly the two rooms it names")
    void everyDoorIsOnASharedPlane() {
        List<String> wrong = new ArrayList<>();
        for (Door door : AquariumStructure.doors(OX, BASE, OZ)) {
            BoundingBox c = door.cells();
            BoundingBox a = door.a().box(OX, BASE, OZ);
            BoundingBox b = door.b().box(OX, BASE, OZ);
            for (int x = c.minX(); x <= c.maxX(); x++) {
                for (int y = c.minY(); y <= c.maxY(); y++) {
                    for (int z = c.minZ(); z <= c.maxZ(); z++) {
                        BlockPos cell = new BlockPos(x, y, z);
                        if (!a.isInside(cell) || !b.isInside(cell)) {
                            wrong.add(door.a() + "-" + door.b() + " has a cell outside one of its rooms at " + cell);
                        }
                        for (Room third : Room.values()) {
                            if (third != door.a() && third != door.b()
                                    && third.box(OX, BASE, OZ).isInside(cell)) {
                                wrong.add(door.a() + "-" + door.b() + " also cuts into " + third + " at " + cell);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("; ", wrong));
    }

    @Test
    @DisplayName("no two rooms share interior air")
    void interiorsDoNotOverlap() {
        List<String> wrong = new ArrayList<>();
        Room[] rooms = Room.values();
        for (int i = 0; i < rooms.length; i++) {
            for (int j = i + 1; j < rooms.length; j++) {
                if (rooms[i].interior(OX, BASE, OZ).intersects(rooms[j].interior(OX, BASE, OZ))) {
                    wrong.add(rooms[i] + " and " + rooms[j] + " overlap");
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("; ", wrong));
    }

    /**
     * Rule 4 of the room graph, and the half of it arithmetic can hold: the water sits inside the
     * guardian tank, every lateral and downward neighbour of a water cell is either water or the
     * tank's own shell, and no doorway reaches down to the waterline. A source with an air neighbour
     * drains, and placeBlock schedules the tick that drains it.
     */
    @Test
    @DisplayName("the guardian tank's water is bounded on every side and the breach is above it")
    void theGuardianWaterIsBounded() {
        assertBoundedInside(AquariumStructure.guardianWater(OX, BASE, OZ), Room.GUARDIAN_TANK, "water");
        BoundingBox water = AquariumStructure.guardianWater(OX, BASE, OZ);
        for (Door door : AquariumStructure.doors(OX, BASE, OZ)) {
            if (door.joins(Room.GUARDIAN_TANK)) {
                assertTrue(door.cells().minY() > water.maxY(),
                    "the guardian tank's breach at " + door.cells() + " reaches the waterline of "
                        + water + ", so the tank empties into the gallery");
            }
        }
    }

    @Test
    @DisplayName("the sump and the gallery bays are bounded the same way")
    void theLeachateIsBounded() {
        assertBoundedInside(AquariumStructure.sump(OX, BASE, OZ), Room.FILTRATION_HALL, "sump");
        for (BoundingBox bay : AquariumStructure.bays(OX, BASE, OZ)) {
            assertBoundedInside(bay, Room.GALLERY, "bay");
        }
    }

    @Test
    @DisplayName("the fixtures sit in the rooms the spec puts them in")
    void theFixturesAreInTheirRooms() {
        assertTrue(Room.BIG_TANK.interior(OX, BASE, OZ).isInside(AquariumStructure.pedestal(OX, BASE, OZ)),
            "the heart of the sea is not in the centrepiece tank");
        assertTrue(AquariumStructure.guardianWater(OX, BASE, OZ).isInside(AquariumStructure.guardianSpawner(OX, BASE, OZ)),
            "the guardian spawner is not in the water");
        assertTrue(Room.BACK_OF_HOUSE.interior(OX, BASE, OZ).isInside(AquariumStructure.chest(OX, BASE, OZ)),
            "the curator's chest is not in back of house");
        assertTrue(Room.BACK_OF_HOUSE.interior(OX, BASE, OZ).isInside(AquariumStructure.drownedSpawner(OX, BASE, OZ)),
            "the drowned spawner is not in back of house");
        assertTrue(Room.FILTRATION_HALL.box(OX, BASE, OZ).isInside(
            new BlockPos(AquariumStructure.silt(OX, BASE, OZ).minX(), AquariumStructure.silt(OX, BASE, OZ).minY(),
                AquariumStructure.silt(OX, BASE, OZ).minZ())), "the silt is not in the filtration hall");
    }

    @Test
    @DisplayName("the layout does not depend on where it is put")
    void theLayoutIsTranslationInvariant() {
        BoundingBox here = Room.GALLERY.box(OX, BASE, OZ);
        BoundingBox there = Room.GALLERY.box(0, 0, 0);
        assertEquals(here.getXSpan(), there.getXSpan());
        assertEquals(here.getYSpan(), there.getYSpan());
        assertEquals(here.getZSpan(), there.getZSpan());
        assertEquals(new BlockPos(OX, BASE, OZ), Room.GALLERY.originOf(here),
            "originOf must invert box, or a reloaded piece rebuilds a different room");
    }

    private static void assertBoundedInside(BoundingBox cells, Room room, String what) {
        BoundingBox interior = room.interior(OX, BASE, OZ);
        BoundingBox shell = room.box(OX, BASE, OZ);
        List<String> wrong = new ArrayList<>();
        for (int x = cells.minX(); x <= cells.maxX(); x++) {
            for (int y = cells.minY(); y <= cells.maxY(); y++) {
                for (int z = cells.minZ(); z <= cells.maxZ(); z++) {
                    BlockPos cell = new BlockPos(x, y, z);
                    if (!shell.isInside(cell)) {
                        wrong.add(what + " cell " + cell + " is outside " + room);
                    }
                    for (BlockPos n : new BlockPos[]{cell.west(), cell.east(), cell.north(), cell.south(), cell.below()}) {
                        boolean fluid = cells.isInside(n);
                        boolean solidShellOrFloor = !interior.isInside(n) || n.getY() < interior.minY();
                        if (!fluid && !solidShellOrFloor) {
                            wrong.add(what + " cell " + cell + " has an open neighbour at " + n
                                + " and would drain into the room");
                        }
                    }
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("; ", wrong));
    }
}
