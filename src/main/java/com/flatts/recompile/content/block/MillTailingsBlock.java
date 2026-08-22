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
 * Mill Tailings: the radioactive dump's bulk block, and the only uranium in the world (#285).
 *
 * <p><b>Real, and left in the open.</b> Tailings are what is left after uranium is extracted from ore -
 * sandy, low-grade, and produced in volumes far too large to contain. Moab and Church Rock both sat in
 * unlined heaps beside a river for decades. That is the referent {@code material_economy.md} asks for,
 * and it is why this is a heap on the surface rather than a seam underground.
 *
 * <p><b>It falls</b>, like every {@link SortableBlock}. Loose spoil is loose spoil.
 *
 * <p><b>It does not regrow</b> (owner, 2026-08-22), and that is consistent rather than exceptional:
 * {@code MoundGroundBlock} is written by {@code MoundFeature} alone, so the demolition yard's features
 * already write none of it. The sprawl regrows because you live in it; the frontier does not, because
 * you leave. A deposit is stripped once, and the noisy region gradient means there is always another.
 *
 * <p><b>A sledgehammer, of any tier</b> - see {@link #requiredToolFamily()}. That puts this behind the
 * reclamation ladder exactly as the demolition yard is (the tool needs sticks, sticks need trees), so
 * the two frontier regions are parallel rather than sequential. Deliberate: the region's onset is 1024,
 * so a player finds it while working the yard and cannot strip it until the ladder is finished, which
 * makes the tool a goal rather than a wall.
 */
public class MillTailingsBlock extends SortableBlock {

    /** Wider than rubble's 2-4: a tailings heap is bulk, and bulk is what makes a trip worth it. */
    private static final int MIN_PULLS = 3;
    private static final int MAX_PULLS = 6;

    public static final IntegerProperty SORTED = IntegerProperty.create("sorted", 0, MAX_PULLS - 1);

    public static final ResourceKey<LootTable> TAILINGS_PULLS = ResourceKey.create(
        Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/tailings_pulls"));

    public static final MapCodec<MillTailingsBlock> CODEC = simpleCodec(MillTailingsBlock::new);

    public MillTailingsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MillTailingsBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty sortedProperty() {
        return SORTED;
    }

    @Override
    protected ResourceKey<LootTable> pullTable() {
        return TAILINGS_PULLS;
    }

    @Override
    protected int minPulls() {
        return MIN_PULLS;
    }

    @Override
    protected int maxPulls() {
        return MAX_PULLS;
    }

    /**
     * The representative tier, for viewers only. The gate is {@link #requiredToolFamily()}; Jade draws
     * an item, so a family with no representative would render as "sort by hand".
     */
    @Override
    @Nullable
    protected Item requiredTool() {
        return RCItems.COPPER_SLEDGEHAMMER.get();
    }

    /** Any sledgehammer. Four exist, so naming one would silently exclude three. */
    @Override
    @Nullable
    protected TagKey<Item> requiredToolFamily() {
        return RCTags.SLEDGEHAMMER;
    }
}
