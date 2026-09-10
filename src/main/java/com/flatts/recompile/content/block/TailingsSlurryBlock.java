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
 * Tailings Slurry: the decant pond on a radioactive-dump impoundment (#423, P1.10-R).
 *
 * <p>Tailings are pumped into an impoundment as a slurry and the process water settles out on top.
 * It is the pond you can see from across the region, and until 2026-09-08 it was plain water tinted
 * by the biome. It is now a fluid of its own - neither water nor {@link LeachateBlock} - because
 * leachate means rain drained through refuse and this does not, and because a custom fluid carries
 * its own tint so the region keeps the pale turquoise its silhouette is built on.
 *
 * <p><b>It is worse than leachate, and that reverses a recorded ruling</b> (owner, 2026-09-08).
 * {@code docs/radioactive_dump_spec.md} section 7 was titled "the hazard ruling this must not walk
 * into" and spelled the 2026-08-05 rule out as <i>no damage, no Poison, no Wither, cannot kill</i>.
 * The reversal is deliberately BOUNDED: Poison cannot kill a player on its own, so of those four
 * clauses only <i>no Poison</i> falls. A hazard here that can actually kill is still a reversal
 * nobody has taken, and Mekanism's radiation - still deferred - will land on this same region later,
 * so the two want looking at as a pair when it does.
 *
 * <p><b>Why this region gets a hazard at all.</b> It had none. Its identity came entirely from what
 * is found in it and what it looks like, precisely because the radiation went to Mekanism, so the one
 * region whose whole premise is contamination was also the one where nothing could hurt you.
 *
 * <p><b>Poison kills nothing, mob or player.</b> Vanilla {@code PoisonMobEffect.applyEffectTick} only
 * deals damage while {@code getHealth() > 1.0F}, and that floor applies to every {@code LivingEntity},
 * so an animal that wanders a pond is left at half a heart exactly as a survival player is - the same
 * parity leachate was written for. What can kill in a pond is drowning, which {@code RCLeachateContact}
 * applies to both fluids off one switch. (This note once said Poison has no floor for mobs; the 26.1.2
 * source says otherwise, #433.)
 *
 * <p><b>The block does not call this - {@code RCLeachateContact} does</b>, for the reason its own
 * class note gives at length: {@code Block.entityInside} is never invoked for a fluid, and the two
 * NeoForge APIs that would be the answer in any other version are commented out in 26.1.
 */
public class TailingsSlurryBlock extends LiquidBlock {

    public TailingsSlurryBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    /**
     * Hunger, and then Poison on top of it.
     *
     * <p>Both are <b>refreshed rather than stacked</b> - {@code addEffect} with an equal amplifier
     * replaces the remaining duration instead of extending it - which matters far more for the Poison
     * than it ever did for the Hunger. Stacking would let a long wade bank minutes of damage and fire
     * them off after the player had climbed out, which is a delayed execution rather than a hazard.
     *
     * <p>The two exemptions are leachate's and are here for leachate's reasons. <b>The Scrap Hauler is
     * exempt explicitly</b>, and invulnerability does not cover it: sickening is a mob EFFECT and
     * effects ignore {@code isInvulnerable} entirely, so without this line an indestructible machine
     * would be poisoned by a pond it is standing in (#376). <b>A creative player is not the subject of
     * a hazard</b>, the same carve-out the mining gate and encroachment make.
     *
     * @return true if the effects were applied, so a test can tell "did nothing" from "was exempt"
     */
    public static boolean sicken(Level level, Entity entity) {
        if (level.isClientSide() || !RCConfig.TAILINGS_SLURRY_SICKENS.get()) {
            return false;
        }
        if (!(entity instanceof LivingEntity living)) {
            return false;
        }
        if (entity instanceof com.flatts.recompile.content.entity.ScrapHaulerEntity) {
            return false;
        }
        if (living instanceof Player player && player.getAbilities().instabuild) {
            return false;
        }
        living.addEffect(new MobEffectInstance(
            MobEffects.HUNGER, RCConfig.TAILINGS_SLURRY_SICKNESS_TICKS.get(), 0, true, true));
        living.addEffect(new MobEffectInstance(
            MobEffects.POISON, RCConfig.TAILINGS_SLURRY_POISON_TICKS.get(), 0, true, true));
        return true;
    }
}
