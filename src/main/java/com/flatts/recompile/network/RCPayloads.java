package com.flatts.recompile.network;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.menu.ScrapCraftingStationMenu;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The mod's custom network channel, both belonging to the Scrap Crafting Table: the connected-storage
 * panel going out (P2.10), and JEI's transfer button coming back (#95). The handler stays side-safe by touching the player through the payload
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
        registrar.playToServer(FillGridPayload.TYPE, FillGridPayload.STREAM_CODEC,
            RCPayloads::onFillGrid);
    }

    /**
     * JEI's transfer button, arriving from the client (#95).
     *
     * <p>The menu check is the authorisation: a payload only does anything to a table the sender
     * currently has open, so it cannot be used to reach into someone else's.
     */
    private static void onFillGrid(FillGridPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ScrapCraftingStationMenu menu) {
                menu.fillGrid(payload.items());
            }
        });
    }

    private static void onContents(ScrapNetworkContentsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ScrapCraftingStationMenu menu) {
                menu.setContents(payload);
            }
        });
    }
}
