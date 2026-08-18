package com.flatts.recompile.event;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.LeachateBlock;
import com.flatts.recompile.registry.RCFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Notices an entity standing in leachate (#156).
 *
 * <p><b>This exists because the obvious hook does not work and the two documented ones are gone.</b>
 * Worth writing down, because all three are the first thing anybody would reach for:
 *
 * <ul>
 *   <li>{@code Block.entityInside} is never called for a fluid. It is reachable only from
 *       {@code Entity.checkInsideBlocks}, which is private and runs from {@code Entity.move}, and
 *       vanilla routes fluid effects - lava damage, extinguishing - through the fluid path instead.
 *       A {@link LeachateBlock} override compiled, looked right, and did nothing in a real game;
 *       {@code walking_into_leachate_really_reaches_the_block} is the test that caught it.
 *   <li>NeoForge's {@code IEntityExtension.isInFluidType} family is <b>commented out</b> in 26.1.
 *       Every mod tutorial uses it and it is not there to call.
 *   <li>Vanilla's own {@code Entity.getFluidHeight} is keyed on fluid <i>tags</i>, and the tag that
 *       would make it fire is {@code #minecraft:water} - which leachate must stay out of, because
 *       that is what would let it irrigate farmland and fill a Rain Collector.
 * </ul>
 *
 * <p>So the fluid is read straight off the world on the entity's own tick. That is cheap enough:
 * one {@code getFluidState} at a position the tick already has, and only for living entities.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCLeachateContact {

    private RCLeachateContact() {
    }

    @SubscribeEvent
    static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof LivingEntity)) {
            return;
        }
        // Feet, not eyes. A pool is one block deep, so an eye check would never fire on anything
        // taller than a chicken and the hazard would be invisible to the player it is aimed at.
        if (entity.level().getFluidState(entity.blockPosition()).getType() == RCFluids.LEACHATE.get()) {
            LeachateBlock.sicken(entity.level(), entity);
        }
        drown(entity);
    }

    /**
     * Drown anything with leachate over its head (owner ruling, 2026-08-17).
     *
     * <p><b>The fluid's own {@code canDrown} flag cannot do this, and setting it was wasted motion.</b>
     * NeoForge's {@code CommonHooks.onLivingBreathe} - the only consumer of {@code canDrownIn} - is
     * commented out in 26.1.2.76, and the patched {@code LivingEntity.baseTick} calls vanilla
     * {@code isEyeInFluid(FluidTags.WATER)} directly. Leachate is deliberately in no fluid tag, so that
     * check can never see it. The flag has therefore been inert since the fluid shipped, which is the
     * real reason it never drowned anyone - not the "pools are one block deep" story it carried, and
     * not the depth ruling that replaced it.
     *
     * <p>This class's own javadoc already said the fluid-type family is commented out in 26.1. The
     * lesson is not the API, it is that the answer was written down in the file that owns the
     * behaviour, and a flag was flipped in a different file without reading it.
     *
     * <p><b>Eyes, not feet</b>, which is the opposite of the Hunger check above and correct for the
     * opposite reason: drowning is about what is over your head, and a one-block pool genuinely can be,
     * for a player who crawls or swims. Vanilla's own numbers are mirrored - one air per tick down, two
     * damage each time it runs out - so it behaves like drowning rather than like a bespoke hazard.
     */
    public static void drownOnce(Entity entity) {
        drown(entity);
    }

    private static void drown(Entity entity) {
        if (!RCConfig.LEACHATE_DROWNS.get() || !(entity instanceof LivingEntity living)) {
            return;
        }
        // A creative player is not the subject of a hazard, the same exemption sicken() makes.
        if (living instanceof Player player && player.getAbilities().invulnerable) {
            return;
        }
        BlockPos eye = BlockPos.containing(living.getX(), living.getEyeY(), living.getZ());
        if (living.level().getFluidState(eye).getType() != RCFluids.LEACHATE.get()) {
            return;
        }
        int air = living.getAirSupply();
        if (air > -20) {
            living.setAirSupply(air - 1);
            return;
        }
        living.setAirSupply(0);
        living.hurt(living.damageSources().drown(), 2.0F);
    }
}
