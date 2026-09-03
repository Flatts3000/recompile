package com.flatts.recompile.client;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCBlockEntities;
import com.flatts.recompile.registry.RCEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only wiring. Dist.CLIENT so it is never classloaded on a dedicated server (the renderer it
 * registers references client-only types); the merged bus fires the mod-lifecycle registration events
 * here just like everywhere else.
 *
 * <p>Registers the mod's single BlockEntityRenderer - the Display Pedestal's trophy renderer, the
 * scoped reversal of P1.11.6 (see {@link DisplayPedestalRenderer}).
 */
@EventBusSubscriber(modid = Recompile.MOD_ID, value = Dist.CLIENT)
public final class RecompileClientEvents {

    private RecompileClientEvents() {
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RCBlockEntities.DISPLAY_PEDESTAL.get(), DisplayPedestalRenderer::new);
        // The Roach (#78) - the mod's first ENTITY renderer, alongside its one block-entity renderer.
        event.registerEntityRenderer(RCEntities.ROACH.get(), RoachRenderer::new);
        event.registerEntityRenderer(RCEntities.PIGEON.get(), PigeonRenderer::new);
        // A garbage block in flight to a vacuum (#336). An ENTITY renderer, so it costs no rule: the
        // block-entity-renderer exception stays scoped to the pedestal.
        event.registerEntityRenderer(RCEntities.VACUUMED_BLOCK.get(), VacuumedBlockRenderer::new);
    }
}
