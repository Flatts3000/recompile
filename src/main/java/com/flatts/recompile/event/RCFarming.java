package com.flatts.recompile.event;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.Recompile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Farming tier (rung 3): farmland comes only from the compost economy - the {@code Fertilizer + dirt ->
 * minecraft:farmland} recipe (26.1 gives farmland a real item, so the recipe outputs it directly, no
 * custom block or item). This cancels hoe-tilling so that recipe stays the canonical path even in a
 * modpack that adds a hoe - the base mod has none. No mixin; NeoForge's tool-modification event.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCFarming {

    private RCFarming() {
    }

    @SubscribeEvent
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (RCConfig.DISABLE_HOE_TILLING.get() && event.getItemAbility() == ItemAbilities.HOE_TILL) {
            event.setCanceled(true);
        }
    }
}
