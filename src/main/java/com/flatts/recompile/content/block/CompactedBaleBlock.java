package com.flatts.recompile.content.block;

import com.flatts.recompile.registry.RCItems;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

/**
 * Compacted bale (design P1.1): dense mixed trash, roughly 2x a garbage block's
 * pulls, opened by cutting the strapping with a scrap knife. Draws the dense
 * household pull table. Generates in mound cores. The batch-friendly input to the
 * Sorting Tarp.
 */
public class CompactedBaleBlock extends SortableBlock {

    private static final int MIN_PULLS = 6;
    private static final int MAX_PULLS = 8;

    public static final IntegerProperty SORTED = IntegerProperty.create("sorted", 0, MAX_PULLS - 1);

    public CompactedBaleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected IntegerProperty sortedProperty() {
        return SORTED;
    }

    @Override
    protected ResourceKey<LootTable> pullTable() {
        return GarbageBlock.HOUSEHOLD_PULLS;
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
        return RCItems.SCRAP_KNIFE.get();
    }
}
