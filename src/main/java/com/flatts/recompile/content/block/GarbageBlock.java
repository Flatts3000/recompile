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
 * The household Block of Garbage (design P0.3 + P0.4): the bulk block. Bare-hand
 * pick-through pulls one drop from the household region table; crumbles after 4-6
 * pulls. All the sort machinery lives in {@link SortableBlock}. Gravity (P0.3) is
 * deferred to a later pass.
 */
public class GarbageBlock extends SortableBlock {

    private static final int MIN_PULLS = 4;
    private static final int MAX_PULLS = 6;

    public static final IntegerProperty SORTED = IntegerProperty.create("sorted", 0, MAX_PULLS - 1);

    /** The household region's pull table (design: region-weighted pulls, JSON per region). */
    public static final ResourceKey<LootTable> HOUSEHOLD_PULLS = ResourceKey.create(
        Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/household_pulls"));

    public static final MapCodec<GarbageBlock> CODEC = simpleCodec(GarbageBlock::new);

    public GarbageBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends GarbageBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty sortedProperty() {
        return SORTED;
    }

    @Override
    protected ResourceKey<LootTable> pullTable() {
        return HOUSEHOLD_PULLS;
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
