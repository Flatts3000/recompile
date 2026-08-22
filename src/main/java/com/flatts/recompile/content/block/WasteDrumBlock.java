package com.flatts.recompile.content.block;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCItems;
import com.flatts.recompile.registry.RCTags;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jspecify.annotations.Nullable;

/**
 * A Waste Drum: the 55-gallon steel drum, yellow, trefoil on the side (#285).
 *
 * <p><b>The object that says what the place is.</b> Low-level radioactive waste really is drummed, and
 * a rusting drum in a landfill is the single most legible image of the whole idea. Rarer than
 * {@link MillTailingsBlock} and better to open, so it is the punctuation in a field of tailings rather
 * than the thing the economy runs on.
 *
 * <p><b>A prybar, like Bulky Waste</b> - you lever the lid off. It keeps its own pull stream rather
 * than sharing the tailings one, because what someone sealed in a drum is not what washed out of a
 * heap: the drum is where the consumer-scale finds live.
 */
public class WasteDrumBlock extends SortableBlock {

    /** Short: a drum has a lid and a bottom, not a seam to work. */
    private static final int MIN_PULLS = 2;
    private static final int MAX_PULLS = 4;

    public static final IntegerProperty SORTED = IntegerProperty.create("sorted", 0, MAX_PULLS - 1);

    public static final ResourceKey<LootTable> DRUM_PULLS = ResourceKey.create(
        Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/waste_drum_pulls"));

    public static final MapCodec<WasteDrumBlock> CODEC = simpleCodec(WasteDrumBlock::new);

    public WasteDrumBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends WasteDrumBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty sortedProperty() {
        return SORTED;
    }

    @Override
    protected ResourceKey<LootTable> pullTable() {
        return DRUM_PULLS;
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
        return RCItems.PRYBAR.get();
    }
}
