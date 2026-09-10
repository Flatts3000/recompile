package com.flatts.recompile.content.freight;

import com.flatts.recompile.Recompile;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * One advancement per freight rung, under a shared root (#434, spec {@code docs/freight_conversion_spec.md}
 * section 2.5).
 *
 * <p><b>Plain named advancements, not a custom criterion trigger.</b> That is the owner's ruling, and
 * the reason is the consumer: FTB Quests, and anything else a pack uses, can already watch an
 * advancement by id, so {@code recompile:freight/tier_3} is a hook with no new API surface. Each file
 * carries one {@code minecraft:impossible} criterion named {@value #CRITERION}, and this class is the
 * only thing that grants it. The root exists so the rungs group in the advancement screen rather than
 * appearing as orphans.
 *
 * <p><b>The tier is the WORLD's, so the advancement goes to everyone.</b> {@link FreightState} keeps one
 * ladder per save, and completion is announced to every player in the world; granting the advancement
 * to only the player who happened to feed the last item would give a pack's quest line to whoever
 * stood nearest. And because a player can join after a rung ships, login grants every rung the world
 * has already reached - otherwise a late partner's quest book would stay locked behind deliveries that
 * already happened.
 *
 * <p><b>It follows the data, not a count.</b> A rung whose file does not exist is skipped quietly, so a
 * pack that adds a ninth phase adds {@code tier_9.json} beside it and needs no Java. Removing one is
 * not free: each rung's parent is the rung before, so deleting a middle file orphans every rung after
 * it - the game logs "Couldn't load advancements" and leaves them out of the screen, though they are
 * still granted and tracked by id. {@code every_freight_phase_has_an_advancement} holds the shipped
 * ladder to having one each.
 */
@EventBusSubscriber(modid = Recompile.MOD_ID)
public final class FreightAdvancements {

    /** The one criterion in every freight advancement file. */
    public static final String CRITERION = "delivered";

    private FreightAdvancements() {
    }

    public static Identifier root() {
        return Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "freight/root");
    }

    public static Identifier tier(int tier) {
        return Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "freight/tier_" + tier);
    }

    /** Grant the root and every rung from 1 through {@code tier}. Idempotent: done criteria stay done. */
    public static void awardThrough(ServerPlayer player, int tier) {
        if (tier < 1) {
            return;
        }
        award(player, root());
        for (int t = 1; t <= tier; t++) {
            award(player, tier(t));
        }
    }

    private static void award(ServerPlayer player, Identifier id) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(id);
        if (holder != null) {
            player.getAdvancements().award(holder, CRITERION);
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            awardThrough(player, FreightState.of(player.level()).tier());
        }
    }
}
