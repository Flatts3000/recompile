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
 * Trash bag (design P1.1): soft surface litter. Bare-hand, quick small pulls (1-2),
 * a lighter pull table than the garbage block. Generates on mound surfaces.
 *
 * <p>~1.5 pulls by hand against the Sorting Tarp's 3 - see {@link SortableBlock}.
 */
public class TrashBagBlock extends SortableBlock {

    private static final int MIN_PULLS = 1;
    private static final int MAX_PULLS = 2;

    public static final IntegerProperty SORTED = IntegerProperty.create("sorted", 0, MAX_PULLS - 1);

    public static final ResourceKey<LootTable> BAG_PULLS = ResourceKey.create(
        Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/bag_pulls"));

    public static final MapCodec<TrashBagBlock> CODEC = simpleCodec(TrashBagBlock::new);

    public TrashBagBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends TrashBagBlock> codec() {
        return CODEC;
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
