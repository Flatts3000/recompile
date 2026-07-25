package com.flatts.recompile.client;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Binds the mod's one custom menu to its screen (client-only). The Scrap Crafting Table is the sole
 * block that needs a bespoke screen - for its connected-storage panel (design P2.10 flow 4); every
 * other container reuses a vanilla screen.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID, value = Dist.CLIENT)
public final class RCMenuScreens {

    private RCMenuScreens() {
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(RCMenus.SCRAP_CRAFTING_STATION.get(), ScrapCraftingStationScreen::new);
    }
}
