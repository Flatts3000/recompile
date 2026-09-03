package com.flatts.recompile.content.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Garbage Vacuum's tier table (#336), pinned as arithmetic.
 *
 * <p>Pure logic, so a unit test is the right instrument: no world, no registry. What it protects is the
 * shape of the ladder - each rung reaches further and holds more than the last - and the cost formula
 * the GameTests then exercise against real blocks.
 */
class VacuumTierTest {

    @Test
    @DisplayName("the ladder climbs: every tier reaches further and holds more than the one below")
    void ladderIsMonotonic() {
        for (int i = 1; i < VacuumTier.LADDER.size(); i++) {
            VacuumTier below = VacuumTier.LADDER.get(i - 1);
            VacuumTier above = VacuumTier.LADDER.get(i);
            assertTrue(above.radius() > below.radius(), above.name() + " must reach further than " + below.name());
            assertTrue(above.capacity() > below.capacity(), above.name() + " must hold more than " + below.name());
        }
        assertEquals(4, VacuumTier.LADDER.size(), "copper, iron, diamond, netherite - the sledgehammer's ladder");
    }

    @Test
    @DisplayName("cost tracks worth: rolls times FE_PER_ROLL, and a non-sortable costs nothing")
    void costTracksRolls() {
        assertEquals(0, VacuumTier.costFor(0), "zero rolls is not a sortable input");
        assertEquals(0, VacuumTier.costFor(-3), "a negative roll count is garbage-in and must not refund");
        assertEquals(4 * VacuumTier.FE_PER_ROLL, VacuumTier.costFor(4), "a Trash Bag");
        assertEquals(6 * VacuumTier.FE_PER_ROLL, VacuumTier.costFor(6), "a Block of Garbage");
        assertEquals(10 * VacuumTier.FE_PER_ROLL, VacuumTier.costFor(10), "Mill Tailings");
    }

    @Test
    @DisplayName("a copper charge clears a real mound's worth of garbage, not a handful")
    void copperClearsAMound() {
        int blocks = VacuumTier.COPPER.blocksPerCharge(6);
        assertTrue(blocks >= 50 && blocks <= 100,
            "copper should take roughly one mound of Blocks of Garbage per charge, got " + blocks);
        assertEquals(0, VacuumTier.COPPER.blocksPerCharge(0), "nothing costs nothing and counts as nothing");
    }

    @Test
    @DisplayName("the item bar spans its 13 pixels from flat to full and never overflows")
    void barWidth() {
        assertEquals(0, VacuumTier.COPPER.barWidth(0));
        assertEquals(13, VacuumTier.COPPER.barWidth(VacuumTier.COPPER.capacity()));
        assertEquals(13, VacuumTier.COPPER.barWidth(VacuumTier.COPPER.capacity() * 3), "over-full clamps");
        assertEquals(0, VacuumTier.COPPER.barWidth(-500), "a negative charge draws as flat");
        int half = VacuumTier.COPPER.barWidth(VacuumTier.COPPER.capacity() / 2);
        assertTrue(half == 6 || half == 7, "half a charge is about half a bar, got " + half);
    }

    @Test
    @DisplayName("the intake volume is the cube the radius implies")
    void intakeVolume() {
        assertEquals(125, VacuumTier.COPPER.intakeVolume(), "radius 2 is a 5x5x5 cube");
        assertEquals(343, VacuumTier.IRON.intakeVolume(), "radius 3 is a 7x7x7 cube");
        assertEquals(1331, VacuumTier.NETHERITE.intakeVolume(), "radius 5 is an 11x11x11 cube");
    }
}
