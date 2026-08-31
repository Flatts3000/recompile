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
 * A pile of flattened boxes (#309): surface litter that comes apart into cardboard.
 *
 * <p><b>Cardboard is found as a PILE rather than as a pull-stream entry</b> (owner, 2026-08-31).
 * The first version of this made {@code recompile:cardboard} a weighted entry in
 * {@code household_pulls}, which works and is invisible: you would occasionally get cardboard out of
 * a garbage block and never see any in the world. A dump full of boxes should look like a dump full
 * of boxes. So it is its own block, it generates on mound surfaces the way the trash bag does, and
 * taking one apart is where the material comes from.
 *
 * <p><b>That also puts the cost somewhere honest.</b> A weighted entry makes every other entry in
 * the stream rarer, which is a change to seven unrelated drop rates paid for by one new material.
 * A pile takes mound CELLS instead, so what it competes with is the number of garbage blocks in a
 * mound - visible in the world, and the thing {@code FindRateTest} already measures every household
 * rate against.
 *
 * <p><b>Bare hands, like the bag and unlike the bale.</b> Flattened cardboard is the one thing in a
 * dump that genuinely needs no tool, and the whole point of this family is that it asks for nothing
 * (see {@code RCBlocks.cardboardProps}). A tool gate here would be the one step that undoes it.
 *
 * <p>2 to 3 pulls, so a pile is worth roughly one Cardboard Block, which is the rate that makes
 * "clear the boxes off a mound, build a wall" a thing you can actually do on the first day.
 *
 * @see SortableBlock
 */
public class CardboardPileBlock extends SortableBlock {

    private static final int MIN_PULLS = 2;
    private static final int MAX_PULLS = 3;

    public static final IntegerProperty SORTED = IntegerProperty.create("sorted", 0, MAX_PULLS - 1);

    /**
     * Its own stream, not the bag's.
     *
     * <p>A pile of boxes is not a bag of household refuse, and pointing it at {@code bag_pulls}
     * would have been the cheap way to skip writing a table. It would also mean the block that
     * exists to be a cardboard source hands back cardboard no more often than anything else does.
     */
    public static final ResourceKey<LootTable> CARDBOARD_PULLS = ResourceKey.create(
        Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/cardboard_pulls"));

    public static final MapCodec<CardboardPileBlock> CODEC = simpleCodec(CardboardPileBlock::new);

    public CardboardPileBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends CardboardPileBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty sortedProperty() {
        return SORTED;
    }

    @Override
    protected ResourceKey<LootTable> pullTable() {
        return CARDBOARD_PULLS;
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
