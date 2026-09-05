package com.flatts.recompile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.recompile.content.entity.ScrapHaulerGoal;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * The Hauler's work area is a square of CHUNKS around its Depot (owner, 2026-09-05), and the
 * arithmetic that decides membership is pure, so it is pinned here with no world. The case that
 * matters is the negative-coordinate one: {@code >> 4} floors and {@code / 16} truncates toward zero,
 * so a Depot at x = -1 and a pile at x = 0 are in DIFFERENT chunks, which a division would get wrong.
 */
class ScrapHaulerAreaTest {

    @Test
    void radiusZeroIsTheDepotsOwnChunk() {
        BlockPos depot = new BlockPos(20, 64, 20);
        assertTrue(ScrapHaulerGoal.inWorkArea(depot, 0, new BlockPos(31, 70, 16)), "same chunk, other corner");
        assertFalse(ScrapHaulerGoal.inWorkArea(depot, 0, new BlockPos(32, 64, 20)), "next chunk east");
        assertFalse(ScrapHaulerGoal.inWorkArea(depot, 0, new BlockPos(20, 64, 15)), "next chunk north");
    }

    @Test
    void radiusOneIsThreeByThree() {
        BlockPos depot = new BlockPos(20, 64, 20);
        assertTrue(ScrapHaulerGoal.inWorkArea(depot, 1, new BlockPos(32, 64, 20)), "one chunk east");
        assertTrue(ScrapHaulerGoal.inWorkArea(depot, 1, new BlockPos(0, 64, 47)), "diagonal corner chunk");
        assertFalse(ScrapHaulerGoal.inWorkArea(depot, 1, new BlockPos(48, 64, 20)), "two chunks east");
    }

    @Test
    void negativeCoordinatesFloorRatherThanTruncate() {
        BlockPos depot = new BlockPos(-1, 64, -1);
        assertFalse(ScrapHaulerGoal.inWorkArea(depot, 0, new BlockPos(0, 64, 0)),
            "x = -1 and x = 0 are different chunks; a / 16 would say otherwise");
        assertTrue(ScrapHaulerGoal.inWorkArea(depot, 1, new BlockPos(0, 64, 0)), "adjacent across the origin");
        assertTrue(ScrapHaulerGoal.inWorkArea(depot, 0, new BlockPos(-16, 64, -16)), "far corner of chunk -1,-1");
    }

    @Test
    void heightIsNotPartOfTheArea() {
        BlockPos depot = new BlockPos(20, 64, 20);
        assertTrue(ScrapHaulerGoal.inWorkArea(depot, 0, new BlockPos(20, 200, 20)), "the area is columns; height is the goal's own rule");
    }
}
