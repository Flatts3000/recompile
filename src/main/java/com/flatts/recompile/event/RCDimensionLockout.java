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
 * Dimension lockout (design P1.8): keep vanilla dimensions from leaking free resources into the closed
 * trash economy until each themed dimension ships.
 *
 * <p><b>The Nether is now OPEN</b> (owner, 2026-08-19: "Nether resources and progression are the
 * reasons to go to the Nether. Portals should be enabled."). Only the End is still held.
 *
 * <p><b>The portal-formation hook is the half that matters, and it is gated on the same flag on
 * purpose.</b> Cancelling travel alone would let a player build a frame, light it, watch it open, and
 * then bounce off an invisible wall - so the frame is refused instead and there are no dead portals
 * standing around. It follows that flipping the flag on has to enable both, which it does.
 *
 * <p><b>What being open costs, until the themed generation ships.</b> The world preset's
 * {@code minecraft:the_nether} entry is still stock vanilla - {@code minecraft:nether} noise settings
 * and the vanilla biome preset - so the dimension currently hands over gold ore, quartz, glowstone,
 * ancient debris, blaze rods and piglin bartering directly. Several of those have designed found
 * sources in {@code material_economy.md} and one of them, gold, is deliberately gated behind
 * E-Scrap to Circuit Powder to a Cupola blast. That gate is not enforced by this class and is not
 * broken by this class; it is simply routed around by a dimension nobody has themed yet. The themed
 * build is what closes it.
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
