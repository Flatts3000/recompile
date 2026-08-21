package com.flatts.recompile.content.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Removes one named item from every loot roll in the game. The generic sibling of
 * {@link StripSaplingsModifier}, which does the same for {@code #minecraft:saplings}.
 *
 * <p><b>What it is for: a drop that should exist only when another mod does.</b> The Sky Stone Shard
 * is the first (#276). It is <em>our</em> item, so it is registered in every install and sits in the
 * pull stream unconditionally, and this modifier - whose file is gated on
 * {@code neoforge:not(mod_loaded ae2)} - takes it back out again when AE2 is absent.
 *
 * <p><b>Why the gate has to be at this layer, which took four measured attempts to establish.</b>
 * {@code neoforge:conditions} is read on a whole loot table FILE, a recipe file, an advancement, and
 * - measured while fixing #277 - a {@code loot_modifiers} file. It is NOT read on a loot pool, a loot
 * entry, or a tag file (that last one is silently ignored in 26.1). So the only data-level gates
 * available are "this entire file exists or does not".
 *
 * <p><b>Gating the target of a reference is not the same as gating the reference, and that is the
 * trap this class exists to avoid.</b> #276 first shipped the drop as a conditional loot table
 * reached by a {@code minecraft:loot_table} entry at weight 15. Without AE2 the table did not load,
 * but the entry referencing it still did: it kept its weight, kept winning 15 rolls in 405, and
 * handed back nothing. That is a silent one-in-27 empty pull in the DEFAULT install - the player
 * spends a pull, may crumble the block, and gets no item, with no log line and no message. Measured
 * at 291 items from 300 rolls. It also left a permanent {@code Missing element} loot-validation
 * warning on every world load, pointing at an engine file.
 *
 * <p><b>Why not a modifier that ADDS the drop instead</b>, which is the obvious inverse and was
 * built first. NeoForge ships {@code neoforge:add_table} for exactly that, and it does fire on this
 * mod's pull streams - measured at 3.6% against an intended 3.7%. What it cannot do is aim: the only
 * way to restrict a modifier to one table is {@code neoforge:loot_table_id}, which compares
 * {@code LootContext.getQueriedLootTableId()}, and <b>that is never set on a table rolled
 * programmatically</b>. All five of this mod's roll sites call
 * {@code LootTable.getRandomItems(LootParams)} directly, so the condition matched nothing and the
 * drop rate was zero; with the condition removed the modifier fired on every table in the game.
 * Stripping needs no aim, because the invariant really is global: without AE2 that item is not loot
 * anywhere.
 */
public class StripItemModifier extends LootModifier {

    public static final MapCodec<StripItemModifier> CODEC =
        RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance)
                .and(BuiltInRegistries.ITEM.byNameCodec().fieldOf("item")
                    .forGetter(modifier -> modifier.item))
                .apply(instance, StripItemModifier::new));

    private final Item item;

    public StripItemModifier(LootItemCondition[] conditions, int priority, Item item) {
        super(conditions, priority);
        this.item = item;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        loot.removeIf(stack -> stack.is(this.item));
        return loot;
    }

    @Override
    public MapCodec<? extends LootModifier> codec() {
        return CODEC;
    }
}
