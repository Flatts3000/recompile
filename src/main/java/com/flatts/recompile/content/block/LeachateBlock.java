package com.flatts.recompile.content.block;

import com.flatts.recompile.RCConfig;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * The leachate pond block (#156).
 *
 * <p><b>Standing in it makes you ill, and deliberately nothing worse.</b> Owner call, 2026-08-05,
 * after the alternative was argued down: leachate does no damage, applies no Poison and no Wither,
 * and cannot kill. It gives {@link MobEffects#HUNGER} for a few seconds, refreshed while you stay in
 * it.
 *
 * <p>Hunger rather than Nausea, which was the other candidate. Nausea is a screen-wobble the player
 * reads as the game being unpleasant without learning anything; hunger costs a resource this world
 * actually meters - food here is tin cans and foraged mushrooms - so it is felt, recoverable, and
 * says "that was contaminated" rather than "the screen is broken".
 *
 * <p>It is also the <i>second</i> penalty, not the first. The real cost of leachate is that it is
 * water you cannot use: the Rain Collector's tank takes water and only water, and a pool will not
 * irrigate. That scarcity is the designed consequence; this is a nudge that stops a pond reading as
 * decoration you can wade through for free.
 *
 * <p>Bounded on purpose. The effect is short, the amplifier is zero, and it is refreshed rather than
 * stacked, so crossing a pool costs a little food and standing in one costs a little more. Creative
 * players are exempt the same way every other hazard in this mod exempts them.
 */
public class LeachateBlock extends LiquidBlock {

    public LeachateBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    /**
     * Apply the pool's effect to one entity. The static entry point the GameTests drive, the same way
     * {@code SortableBlock.sortOnce} and {@code MoundGroundBlock.regrowOnce} are.
     *
     * <p><b>The block does not call this - {@code RCLeachateContact} does.</b> A {@code entityInside}
     * override was the first attempt and is never invoked for a fluid; see that class for why, and
     * for the two NeoForge APIs that would have been the answer in any other version.
     *
     * @return true if the effect was applied, so a test can tell "did nothing" from "was exempt"
     */
    public static boolean sicken(Level level, Entity entity) {
        if (level.isClientSide() || !RCConfig.LEACHATE_SICKENS.get()) {
            return false;
        }
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        // A creative player is not the subject of a hazard, the same exemption the mining gate and
        // encroachment already make. Mobs that wander through a pond are affected exactly as a
        // survival player is.
        if (living instanceof Player player && player.getAbilities().instabuild) {
            return false;
        }
        // Refreshed, not stacked: addEffect with an equal amplifier replaces the remaining duration
        // rather than extending it without bound, so standing in a pool holds the effect steady
        // instead of banking minutes of it to fire after you leave.
        living.addEffect(new MobEffectInstance(
            MobEffects.HUNGER, RCConfig.LEACHATE_SICKNESS_TICKS.get(), 0, true, true));
        return true;
    }
}
