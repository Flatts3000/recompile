package com.flatts.recompile.network;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The mod's one custom network channel: the Scrap Crafting Table's connected-storage panel (P2.10).
 * Server -> client only. The handler stays side-safe by touching the player through the payload
 * context (no client-only class is referenced from the registration), so it loads on a dedicated
 * server without issue.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCPayloads {

    private RCPayloads() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ScrapNetworkContentsPayload.TYPE, ScrapNetworkContentsPayload.STREAM_CODEC,
            RCPayloads::onContents);
    }

    private static void onContents(ScrapNetworkContentsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ScrapCraftingStationMenu menu) {
                menu.setContents(payload);
            }
        });
    }
}
