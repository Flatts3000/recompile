package com.flatts.recompile.content.block;

import com.flatts.recompile.Recompile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

/**
 * Trash bag (design P1.1): soft surface litter. Bare-hand, quick small pulls (2-3),
 * a lighter pull table than the garbage block. Generates on mound surfaces.
 */
public class TrashBagBlock extends SortableBlock {

    private static final int MIN_PULLS = 2;
    private static final int MAX_PULLS = 3;

    public static final IntegerProperty SORTED = IntegerProperty.create("sorted", 0, MAX_PULLS - 1);

    public static final ResourceKey<LootTable> BAG_PULLS = ResourceKey.create(
        Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/bag_pulls"));

    public TrashBagBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected IntegerProperty sortedProperty() {
        return SORTED;
    }

    @Override
    protected ResourceKey<LootTable> pullTable() {
        return BAG_PULLS;
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
