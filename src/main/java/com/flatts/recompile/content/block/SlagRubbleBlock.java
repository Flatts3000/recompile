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
 * Slag Rubble: the Nether's loose spoil, and the direct analogue of {@link RubbleBlock} (owner,
 * 2026-08-19 - "the Nether analog to rubble in the demolition zone").
 *
 * <p><b>Named for what it is rather than where it is.</b> "Nether Rubble" was the working name and says
 * only which dimension it sits in; every other block in this mod is named for its material - Stone
 * Rubble, Mechanical Waste, Techno-Organic Waste. This is the fraction that never fused into the
 * compacted fill: burnt, glassy, still warm. It also ties the dimension to a word the player already
 * knows from the Cupola, which is the point at which slag stopped being a byproduct and became a
 * material.
 *
 * <p><b>It falls, and its neighbour does not.</b> {@link TechnoOrganicWasteBlock} is the terrain and
 * cannot slump without collapsing the dimension; this is loose spoil sitting inside that terrain, so
 * gravity is exactly right - undercut a seam and it comes down on you. Same call Stone Rubble makes in
 * the demolition yard.
 */
public class SlagRubbleBlock extends SortableBlock {

    /** Stone Rubble's window. Loose spoil is loose spoil, whichever dimension it fell in. */
    private static final int MIN_PULLS = 2;
    private static final int MAX_PULLS = 4;

    public static final IntegerProperty SORTED = IntegerProperty.create("sorted", 0, MAX_PULLS - 1);

    /** What the burnt fraction yields: the depths' mineral half, against the waste's salvage half. */
    public static final ResourceKey<LootTable> SLAG_RUBBLE_PULLS = ResourceKey.create(
        Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/slag_rubble_pulls"));

    public static final MapCodec<SlagRubbleBlock> CODEC = simpleCodec(SlagRubbleBlock::new);

    public SlagRubbleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends SlagRubbleBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty sortedProperty() {
        return SORTED;
    }

    @Override
    protected ResourceKey<LootTable> pullTable() {
        return SLAG_RUBBLE_PULLS;
    }

    @Override
    protected int minPulls() {
        return MIN_PULLS;
    }

    @Override
    protected int maxPulls() {
        return MAX_PULLS;
    }

    @Override
    @Nullable
    protected Item requiredTool() {
        return null;
    }
}
