package com.flatts.recompile.client;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.client.screen.SortingTarpScreen;
import com.flatts.recompile.registry.RCMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only wiring: bind menus to their screens. */
@EventBusSubscriber(modid = Recompile.MOD_ID, value = Dist.CLIENT)
public final class RCClientEvents {

    private RCClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(RCMenuTypes.SORTING_TARP.get(), SortingTarpScreen::new);
    }
}
