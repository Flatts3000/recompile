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
 * Mechanical Waste - the demolition yard's machinery pile, and the front door to the gem tier
 * ({@code docs/gem_tier_spec.md}). Gearboxes, motors, bearings and cable, picked through bare-hand the
 * same way as everything else in this mod.
 *
 * <p><b>It never drops anything precious.</b> The pull stream is industrial scrap - spent abrasive,
 * magnet scrap, quartz grit - and the diamond, redstone and amethyst are separated out of those
 * downstream. That split is the tier: the pile is the found half, the Separator is the refined half, and
 * a pile that handed out diamonds directly would be a rarer loot table rather than a tier.
 *
 * <p>Sits beside {@link RubbleBlock} in the yard and takes <b>no tool</b>, because rubble takes none and
 * a gate the adjacent block does not have is an inconsistency a player learns for nothing. The yard is
 * already gated by travel. The 3-4 crumble window matches the Compacted Bale, the other dense and rich
 * stream, rather than rubble's looser 2-4; numbers tune in the #36 pass.
 */
public class MechanicalWasteBlock extends SortableBlock {

    private static final int MIN_PULLS = 3;
    private static final int MAX_PULLS = 4;

    public static final IntegerProperty SORTED = IntegerProperty.create("sorted", 0, MAX_PULLS - 1);

    /** Industrial scrap only. Asserted gem-free by {@code mechanical_waste_never_drops_a_gem}. */
    public static final ResourceKey<LootTable> MECHANICAL_PULLS = ResourceKey.create(
        Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "gameplay/mechanical_pulls"));

    public static final MapCodec<MechanicalWasteBlock> CODEC = simpleCodec(MechanicalWasteBlock::new);

    public MechanicalWasteBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends MechanicalWasteBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty sortedProperty() {
        return SORTED;
    }

    @Override
    protected ResourceKey<LootTable> pullTable() {
        return MECHANICAL_PULLS;
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
