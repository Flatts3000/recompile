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
 * <p><b>What being open costs, now that the themed generation has shipped.</b> The preset's
 * {@code minecraft:the_nether} entry points at {@code recompile:compacted_depths} with a single fixed
 * biome, which closes some of this list and leaves the rest. Kept rather than deleted because the
 * remainder is still live and because an earlier version named only gold and stopped, which
 * understated it badly:
 *
 * <ul>
 *   <li><b>Iron</b>, which is gated harder than gold. {@code minecraft:gameplay/piglin_bartering}
 *       yields iron nuggets (and iron boots), and {@code minecraft:chests/nether_bridge} contains iron
 *       ingots outright - so a player reaches iron with no demolition yard, no Cutting Torch and no
 *       Cupola. That is precisely the #91 shape: a gate built from the absence of a material dies the
 *       moment something adds the material, and this adds it.</li>
 *   <li><b>Wood - CLOSED by the themed generation.</b> The vanilla Nether's crimson and warped stems
 *       craft to planks that sit in {@code #minecraft:planks}, which made one trip unlimited planks,
 *       sticks, chests and a vanilla crafting table, and {@code StripSaplingsModifier} keys on
 *       {@code ItemTags.SAPLINGS} and does not cover nether fungi. The compacted depths are one fixed
 *       biome with no fungus forest in it, so there are no stems to cut. The Tree Nursery ladder is
 *       load-bearing again.</li>
 *   <li><b>Gold</b>, deliberately behind E-Scrap to Circuit Powder to a Cupola blast, reachable from
 *       nether gold ore and bartering.</li>
 *   <li>Quartz, glowstone and ancient debris, each of which has a designed found source in
 *       {@code material_economy.md}.</li>
 * </ul>
 *
 * <p>What remains is structure loot and bartering rather than terrain: fortresses and bastions are
 * kept deliberately, so iron and gold still arrive through chests and piglins. None of it is enforced
 * by this class or broken by it - it is the accepted cost of the door being open, now bounded to the
 * structures rather than the whole dimension.
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
