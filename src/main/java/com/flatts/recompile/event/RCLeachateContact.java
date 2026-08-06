package com.flatts.recompile.event;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.LeachateBlock;
import com.flatts.recompile.registry.RCFluids;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
    }
}
