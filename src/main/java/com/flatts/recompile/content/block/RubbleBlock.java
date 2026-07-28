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
 * Rubble - the demolition yard's stone source (demolition_yard_spec.md S4.1). Works exactly like a Block
 * of Garbage: bare-hand pick-through pulls one drop at a time, and it crumbles after a few pulls. Its
 * pull stream is stone shards (one per vanilla stone type), so sifting rubble is the ungated entry
 * reward for braving the region - assemble the shards into their stone block at the Scrap Crafting Table.
 *
 * <p>All the sort machinery lives in {@link SortableBlock}; this only supplies the {@code rubble_pulls}
 * table, the crumble window, and bare-hand access (null tool). A touch richer than a garbage block
 * (2-4 pulls vs 2-3) since a rubble heap holds more than loose litter; numbers tune in the #36 pass.
 */
public class RubbleBlock extends SortableBlock {

    private static final int MIN_PULLS = 2;
    private static final int MAX_PULLS = 4;

    public static final IntegerProperty SORTED = IntegerProperty.create("sorted", 0, MAX_PULLS - 1);

    /** The demolition yard's pull table: stone shards, weighted like scrap. */
    public static final ResourceKey<LootTable> RUBBLE_PULLS = ResourceKey.create(
        Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/rubble_pulls"));

    public static final MapCodec<RubbleBlock> CODEC = simpleCodec(RubbleBlock::new);

    public RubbleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends RubbleBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty sortedProperty() {
        return SORTED;
    }

    @Override
    protected ResourceKey<LootTable> pullTable() {
        return RUBBLE_PULLS;
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
