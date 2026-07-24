package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.block.multiblock.Multiblock;
import com.flatts.recompile.registry.RCBlocks;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
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

    private void assertBlockAt(int x, int y, int z, Block expected) {
        Vec3i offset = new Vec3i(x, y, z);
        boolean found = blueprint.cells().stream()
            .anyMatch(c -> c.offset().equals(offset) && c.component() == expected);
        assertTrue(found, "expected " + expected + " at offset " + offset);
    }
}
