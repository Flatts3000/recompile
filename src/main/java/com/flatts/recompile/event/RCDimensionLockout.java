package com.flatts.recompile.event;

import com.flatts.recompile.RCConfig;
import com.flatts.recompile.Recompile;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Dimension lockout (design P1.8): keep vanilla dimensions from leaking free
 * resources into the closed trash economy until each themed dimension ships.
 * Blocks travel to the Nether/End (config-gated) and stops Nether portals from
 * forming so there are no dead frames. Flip {@code dimensions.netherEnabled} /
 * {@code endEnabled} on when a themed build lands.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class RCDimensionLockout {

    private RCDimensionLockout() {
    }

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        ResourceKey<Level> destination = event.getDimension();
        boolean locked =
            (destination == Level.NETHER && !RCConfig.NETHER_ENABLED.get())
            || (destination == Level.END && !RCConfig.END_ENABLED.get());
        if (!locked) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("message.recompile.dimension_locked"));
        }
    }

    @SubscribeEvent
    public static void onNetherPortalSpawn(BlockEvent.PortalSpawnEvent event) {
        if (!RCConfig.NETHER_ENABLED.get()) {
            event.setCanceled(true);
        }
    }
}
