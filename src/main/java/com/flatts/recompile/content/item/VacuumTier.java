package com.flatts.recompile.content.item;

import java.util.List;

/**
 * The Garbage Vacuum's tier ladder (#336): how far it reaches and how much charge it holds.
 *
 * <p>A ladder rather than an upgrade matrix (owner, 2026-09-03). The issue floated five upgrades;
 * radius and capacity climb together and are tiers, filter, void and network link are a matrix and
 * are deferred. Copper/iron/diamond/netherite is the sledgehammer's shape, so a diamond vacuum and a
 * diamond sledgehammer light up together when diamonds do.
 *
 * <p>Pure, on purpose: no registry, no world, so {@code VacuumTierTest} can pin the numbers without a
 * server. The {@code ToolMaterial} that decides which gated piles a tier may take lives on the item,
 * because a material names tags and tags need the registry bootstrapped.
 */
public record VacuumTier(String name, int radius, int capacity) {

    /**
     * FE per roll of the block taken, so cost tracks worth: a Trash Bag is 4 rolls, a Compacted Bale 8,
     * Mill Tailings 10. Ten per roll puts a Block of Garbage at 60 FE against the Trommel's 640 to
     * SORT one, which is the ratio the two verbs deserve - collecting a block is a fraction of the
     * work of picking through it.
     */
    public static final int FE_PER_ROLL = 10;

    public static final VacuumTier COPPER = new VacuumTier("copper", 2, 4_000);
    public static final VacuumTier IRON = new VacuumTier("iron", 3, 8_000);
    public static final VacuumTier DIAMOND = new VacuumTier("diamond", 4, 16_000);
    public static final VacuumTier NETHERITE = new VacuumTier("netherite", 5, 24_000);

    /** The ladder in creative-tab order, lowest first. */
    public static final List<VacuumTier> LADDER = List.of(COPPER, IRON, DIAMOND, NETHERITE);

    /** What one block of {@code rolls} worth costs to take. Zero rolls is not a sortable and costs nothing. */
    public static int costFor(int rolls) {
        return rolls <= 0 ? 0 : rolls * FE_PER_ROLL;
    }

    /** Width of the 13-pixel item bar for a given charge. */
    public int barWidth(int charge) {
        return Math.round(13.0F * Math.min(Math.max(charge, 0), capacity) / capacity);
    }

    /** How many blocks of a given worth a full charge takes. */
    public int blocksPerCharge(int rolls) {
        int cost = costFor(rolls);
        return cost == 0 ? 0 : capacity / cost;
    }

    /** Cells in the intake volume: a cube of side {@code 2r + 1} around the aim point. */
    public int intakeVolume() {
        int side = 2 * radius + 1;
        return side * side * side;
    }
}
