package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.block.WorkstationCoreBlock;
import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Workstation Core's blueprint (design P2.10). The 6x2x2 layout is defined as a
 * list of offset -> block cells; a wrong offset or block is the easy mistake, and the full 18-block
 * structure is too wide for the 5x5x5 GameTest plot, so this pins the shape here (the mod's loaded
 * unitTest context has the block registry) and the full assembly is a runClient check.
 */
class WorkstationBlueprintTest {

    private final Multiblock blueprint = RCBlocks.WORKSTATION_CORE.get().blueprint();

    @Test
    void hasSeventeenCellsAroundTheCore() {
        // 5 frames + 6 shelf bins + 6 counter blocks = 17 (the core itself is the origin, not a cell).
        assertEquals(17, blueprint.cells().size());
    }

    @Test
    void everyCellIsFormedEqualsComponent() {
        // The whole point: nothing is replaced when forming, so each cell's formed block is its own.
        for (Multiblock.Cell cell : blueprint.cells()) {
            assertEquals(cell.component(), cell.formed(),
                "cell at " + cell.offset() + " must be formed==component (no replacement)");
        }
    }

    @Test
    void theCounterIsTheFiveWorkstationsPlusTheJunkBin() {
        assertBlockAt(0, 0, -1, RCBlocks.SCRAP_CRAFTING_TABLE.get());
        assertBlockAt(1, 0, -1, RCBlocks.RECOMPILE_WORKBENCH.get());
        assertBlockAt(2, 0, -1, RCBlocks.SORTING_TARP.get());
        assertBlockAt(3, 0, -1, RCBlocks.BURN_BARREL.get());
        assertBlockAt(4, 0, -1, RCBlocks.SCRAP_BARREL.get());
        assertBlockAt(5, 0, -1, RCBlocks.SCRAP_BIN.get());   // junk bin at hand level
    }

    @Test
    void theBackRowIsFramesAndTheShelfIsBinsIncludingOneOnTheCore() {
        for (int x = 1; x <= 5; x++) {
            assertBlockAt(x, 0, 0, RCBlocks.MACHINE_FRAME.get());   // 5 frames beside the core
        }
        for (int x = 0; x <= 5; x++) {
            assertBlockAt(x, 1, 0, RCBlocks.SCRAP_BIN.get());       // 6 bins on top, one on the core
        }
    }

    @Test
    void placementFacesTheCoreAwayFromThePlayerSoTheCounterSwingsToTheirSide() {
        // The stored facing is the opposite of the look direction, so the counter (authored behind a
        // NORTH core) ends up on the player's side rather than facing the back of the shelf.
        assertEquals(Direction.SOUTH, WorkstationCoreBlock.facingForPlacement(Direction.NORTH));
        assertEquals(Direction.WEST, WorkstationCoreBlock.facingForPlacement(Direction.EAST));
        assertEquals(Direction.NORTH, WorkstationCoreBlock.facingForPlacement(Direction.SOUTH));
        assertEquals(Direction.EAST, WorkstationCoreBlock.facingForPlacement(Direction.WEST));
    }

    @Test
    void facingMapsToTheRotationThatBuildsTheBenchAhead() {
        // The blueprint is authored for NORTH; each other facing rotates it by the turn from north.
        assertEquals(Rotation.NONE, WorkstationCoreBlock.rotationFromFacing(Direction.NORTH));
        assertEquals(Rotation.CLOCKWISE_90, WorkstationCoreBlock.rotationFromFacing(Direction.EAST));
        assertEquals(Rotation.CLOCKWISE_180, WorkstationCoreBlock.rotationFromFacing(Direction.SOUTH));
        assertEquals(Rotation.COUNTERCLOCKWISE_90, WorkstationCoreBlock.rotationFromFacing(Direction.WEST));
    }

    @Test
    void rotatingTheCounterCellFollowsTheFacing() {
        // The crafting table sits at (0,0,-1): due north of a NORTH-facing core (in front of the
        // player). Turn the core and the whole counter turns with it - the same cell lands north,
        // east, south, west of the core for the four facings, so the bench always builds ahead.
        Vec3i counter = new Vec3i(0, 0, -1);
        assertEquals(new Vec3i(0, 0, -1), Multiblock.rotate(counter, Rotation.NONE));            // north
        assertEquals(new Vec3i(1, 0, 0), Multiblock.rotate(counter, Rotation.CLOCKWISE_90));     // east
        assertEquals(new Vec3i(0, 0, 1), Multiblock.rotate(counter, Rotation.CLOCKWISE_180));    // south
        assertEquals(new Vec3i(-1, 0, 0), Multiblock.rotate(counter, Rotation.COUNTERCLOCKWISE_90)); // west
    }

    @Test
    void cellWorldPositionRotatesAboutTheCore() {
        // Cell.at composes the core position with the rotated offset - the shelf bin on the far end
        // (5,1,0) swings a quarter turn when the core faces east.
        BlockPos core = new BlockPos(100, 64, 100);
        Multiblock.Cell farBin = new Multiblock.Cell(new Vec3i(5, 1, 0), RCBlocks.SCRAP_BIN.get(), RCBlocks.SCRAP_BIN.get());
        assertEquals(core.offset(5, 1, 0), farBin.at(core, Rotation.NONE));
        assertEquals(core.offset(0, 1, 5), farBin.at(core, Rotation.CLOCKWISE_90));
    }

    private void assertBlockAt(int x, int y, int z, Block expected) {
        Vec3i offset = new Vec3i(x, y, z);
        boolean found = blueprint.cells().stream()
            .anyMatch(c -> c.offset().equals(offset) && c.component() == expected);
        assertTrue(found, "expected " + expected + " at offset " + offset);
    }
}
