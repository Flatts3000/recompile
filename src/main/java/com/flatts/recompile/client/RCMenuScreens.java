package com.flatts.recompile.client;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.registry.RCMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Binds the mod's custom menus to their screens (client-only). Two blocks need a bespoke screen: the
 * Scrap Crafting Table (its connected-storage panel, P2.10 flow 4) and the Tree Nursery (its species
 * picker, reclamation rung 4). Every other container reuses a vanilla screen.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID, value = Dist.CLIENT)
public final class RCMenuScreens {

    private RCMenuScreens() {
    }

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(RCMenus.SCRAP_CRAFTING_STATION.get(), ScrapCraftingStationScreen::new);
        event.register(RCMenus.BURNER_GENERATOR.get(), BurnerGeneratorScreen::new);
        event.register(RCMenus.SEQUENCER.get(), SequencerScreen::new);
        event.register(RCMenus.CUPOLA_FURNACE.get(), CupolaFurnaceScreen::new);
        event.register(RCMenus.SLAG_FURNACE.get(), SlagFurnaceScreen::new);
        event.register(RCMenus.SINTERING_KILN.get(), SinteringKilnScreen::new);
        event.register(RCMenus.HYDROPONICS_BAY.get(), HydroponicsBayScreen::new);
        event.register(RCMenus.TREE_NURSERY.get(), TreeNurseryScreen::new);
    }
}
