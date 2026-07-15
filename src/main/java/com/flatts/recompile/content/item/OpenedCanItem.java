package com.flatts.recompile.content.item;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An opened tin can (design P1.9): edible, but the contents are a gamble. Eating it
 * fills the food bar (via the FOOD component set at registration) and then applies
 * one random effect from a curated pool - mostly mild-bad, sometimes good. The
 * "eating sketchy food out of a dump" beat, mechanical not narrated.
 */
public class OpenedCanItem extends Item {

    private static final int EFFECT_TICKS = 200; // 10 seconds
    private static final List<Holder<MobEffect>> POOL = List.of(
        MobEffects.HUNGER,
        MobEffects.POISON,
        MobEffects.WEAKNESS,
        MobEffects.SLOWNESS,
        MobEffects.REGENERATION,
        MobEffects.SATURATION,
        MobEffects.ABSORPTION,
        MobEffects.SPEED);

    public OpenedCanItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // super handles the food nutrition and shrinking the stack (FOOD/CONSUMABLE
        // components), then we roll the mystery effect server-side.
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide()) {
            Holder<MobEffect> pick = POOL.get(entity.getRandom().nextInt(POOL.size()));
            entity.addEffect(new MobEffectInstance(pick, EFFECT_TICKS));
        }
        return result;
    }
}
