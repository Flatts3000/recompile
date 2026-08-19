package com.flatts.recompile.content.block;

import com.flatts.recompile.Recompile;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

/**
 * Techno-organic waste: the Nether's Block of Garbage (owner, 2026-08-19).
 *
 * <p>The compacted depths are the overworld read the other way round - <i>the overworld is a dump you
 * clear, the Nether is a dump you mine</i> - so its bulk block is the direct analogue of
 * {@link GarbageBlock}, down to the four texture variants. Fused machinery and organic refuse,
 * compacted bedrock roof to bedrock floor.
 *
 * <p><b>It does not fall, and that is the one place it departs from its overworld twin.</b> Garbage is
 * a pile on a plain and slumping is the point; this is the terrain. A falling terrain block collapses
 * the dimension the moment anyone tunnels into it - and keeps collapsing, because every block that
 * lands lands on more of itself. See {@link SortableBlock#obeysGravity()}.
 *
 * <p><b>Nether Rubble does fall</b>, for the same reason Stone Rubble does: it is loose spoil sitting
 * in the fill rather than the fill itself.
 */
public class TechnoOrganicWasteBlock extends SortableBlock {

    /**
     * Deeper than household garbage's 2-3.
     *
     * <p>Compacted waste has been under its own weight since the world was buried, and the block is
     * terrain rather than a pile you kick over - so it takes longer to come apart. It is also the only
     * thing to sort for a whole dimension, where household garbage competes with bags, bales and Bulky
     * Waste. Final numbers belong to the balance pass (#36); the shape belongs here.
     */
    private static final int MIN_PULLS = 3;
    private static final int MAX_PULLS = 5;

    public static final IntegerProperty SORTED = IntegerProperty.create("sorted", 0, MAX_PULLS - 1);

    /** What the compacted depths yield. The Nether's own stream, not a reskin of the household one. */
    public static final ResourceKey<LootTable> DEPTHS_PULLS = ResourceKey.create(
        Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/depths_pulls"));

    public static final MapCodec<TechnoOrganicWasteBlock> CODEC =
        simpleCodec(TechnoOrganicWasteBlock::new);

    public TechnoOrganicWasteBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends TechnoOrganicWasteBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty sortedProperty() {
        return SORTED;
    }

    /** The terrain of a dimension cannot slump. See the class javadoc. */
    @Override
    protected boolean obeysGravity() {
        return false;
    }

    /**
     * No roaches down here.
     *
     * <p>Roaches are a household-garbage thing (#78) - a kitchen pest in a kitchen dump. Nothing about
     * fused machinery a hundred blocks under a burning sky says cockroach, and the Nether has its own
     * hostiles from the vanilla spawn rules.
     */
    @Override
    public boolean harboursRoaches() {
        return false;
    }

    @Override
    protected ResourceKey<LootTable> pullTable() {
        return DEPTHS_PULLS;
    }

    @Override
    protected int minPulls() {
        return MIN_PULLS;
    }

    @Override
    protected int maxPulls() {
        return MAX_PULLS;
    }

    /** Bare hands, like household garbage. The depth of the seam is the gate, not a tool. */
    @Override
    @Nullable
    protected Item requiredTool() {
        return null;
    }
}
