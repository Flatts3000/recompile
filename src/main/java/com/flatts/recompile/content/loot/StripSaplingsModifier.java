package com.flatts.recompile.content.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Removes every sapling from every loot roll in the game (design P2.4-R2). <b>No loot roll anywhere
 * yields a sapling</b> - not a broken sapling, not decaying leaves, not a chest - so the Tree Nursery
 * is where trees come from and a sapling is not something a player carries around.
 *
 * <p><b>This javadoc used to say "a player can never obtain a sapling as an item", and that stopped
 * being true on 2026-08-20.</b> Villagers arrived with #227 and a wandering trader sells saplings for
 * emeralds; trades are not loot rolls, so nothing here sees them. Curating trade tables was considered
 * and declined (#263, closed not-planned): trading is accepted as a real alternative route, and the
 * cost of getting a villager at all - a cured zombie villager, a splash potion of weakness, a golden
 * apple - is what stands in for the gates it walks around.
 *
 * <p><b>The mechanism below is unchanged and still worth having.</b> It covers every route except that
 * one, and the reasoning in the next paragraph is why: a sapling picked up off the ground early is a
 * tree on virgin coarse dirt, and one bought after a villager is a tree bought at the price of a
 * villager. The word to be careful with is "never"; the rule itself is intact.
 *
 * <p><b>Why this rule exists.</b> Vanilla lets a sapling be planted, and grown, on raw coarse dirt:
 * {@code VegetationBlock.mayPlaceOn} tests {@code #minecraft:supports_vegetation}, which resolves
 * through {@code #substrate_overworld} to {@code #minecraft:dirt} and so includes coarse dirt, and
 * 26.1's {@code TreeFeature} has no ground-material gate at all (it checks only that the air space
 * is replaceable). A findable sapling could therefore be planted on virgin garbage-world ground and
 * grown into a tree, which under P1.7-R <em>permanently anchors the frontier</em> - rung 3 with no
 * rung 1 or 2 and no machine, in flat contradiction of P2.4-R item 3 ("every green block is paid
 * for by a machine the player built and feeds").
 *
 * <p><b>Why a global modifier rather than loot-table overrides.</b> The rule is an invariant, not a
 * per-block tweak. Overriding vanilla tables would take ~11 leaf tables plus ~11 sapling tables,
 * would silently miss any modded tree a pack adds later, and would miss non-block sources such as
 * chest loot. One modifier keyed off {@code #minecraft:saplings} cannot be forgotten, and covers
 * azalea and the mangrove propagule for free because the tag already does.
 *
 * <p>This is deliberately the whole enforcement mechanism - there is no {@code supports_vegetation}
 * override. Gating on "what can place a sapling" rather than "what soil accepts one" leaves vanilla
 * planting rules intact for every soil the player actually heals.
 */
public class StripSaplingsModifier extends LootModifier {

    public static final MapCodec<StripSaplingsModifier> CODEC =
        RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, StripSaplingsModifier::new));

    public StripSaplingsModifier(LootItemCondition[] conditions, int priority) {
        super(conditions, priority);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        loot.removeIf(stack -> stack.is(ItemTags.SAPLINGS));
        return loot;
    }

    @Override
    public MapCodec<? extends LootModifier> codec() {
        return CODEC;
    }
}
