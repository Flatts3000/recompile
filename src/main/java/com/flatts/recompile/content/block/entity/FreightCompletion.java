package com.flatts.recompile.content.block.entity;

import com.flatts.recompile.content.freight.FreightPhases;
import com.flatts.recompile.content.recipe.FreightPhaseRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/**
 * What happens when a freight phase completes, and deliberately not much (#387).
 *
 * <p><b>The engine attaches no meaning to finishing the ladder.</b> It announces, it makes a noise,
 * and it posts an event. What completion is FOR is pack content and lives in Trashlands; see the
 * engine/pack split in {@code market_spec.md} section 5. Nothing in this class, its lang keys or its
 * sounds may carry narrative.
 *
 * <p><b>Two hooks: an advancement per rung for datapacks, and a bus event for mods.</b> Every player in
 * the world is granted {@code recompile:freight/tier_N} ({@link
 * com.flatts.recompile.content.freight.FreightAdvancements}, #434), which is what a pack's quest line
 * watches. The {@link PhaseCompleted} event stays for another mod that wants the phase itself. The
 * event shipped first, alone, while the spec's advancement waited on a consumer; the spec's ruling was
 * a plain advancement per rung rather than a custom criterion, and that is what shipped.
 */
public final class FreightCompletion {

    private FreightCompletion() {
    }

    /**
     * Fired on the NeoForge event bus when a phase completes. {@code tier} is the tier the world has
     * just reached, so the first completion posts tier 1.
     */
    public static class PhaseCompleted extends Event {
        private final ServerLevel level;
        private final BlockPos pos;
        private final FreightPhaseRecipe phase;
        private final int tier;
        private final boolean ladderFinished;

        public PhaseCompleted(ServerLevel level, BlockPos pos, FreightPhaseRecipe phase, int tier,
                boolean ladderFinished) {
            this.level = level;
            this.pos = pos;
            this.phase = phase;
            this.tier = tier;
            this.ladderFinished = ladderFinished;
        }

        public ServerLevel level() {
            return level;
        }

        public BlockPos pos() {
            return pos;
        }

        public FreightPhaseRecipe phase() {
            return phase;
        }

        public int tier() {
            return tier;
        }

        /** True only on the last rung. This is the one a pack cares about. */
        public boolean ladderFinished() {
            return ladderFinished;
        }
    }

    /** Announce, sound, post. Called from the terminal's ticker after the tier has already moved. */
    public static void onPhaseCompleted(ServerLevel level, BlockPos pos, FreightPhaseRecipe phase,
            int tier) {
        boolean finished = tier >= FreightPhases.length(level);

        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);

        // Everyone in the world, because the tier is the world's rather than a player's. Told in the
        // chat rather than the action bar: it happens once per phase and is worth scrollback.
        Component message = finished
            ? Component.translatable("message.recompile.freight.ladder_finished")
            : Component.translatable("message.recompile.freight.phase_completed",
                Component.translatable(phase.name()), tier);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
            com.flatts.recompile.content.freight.FreightAdvancements.awardThrough(player, tier);
        }

        NeoForge.EVENT_BUS.post(new PhaseCompleted(level, pos, phase, tier, finished));
    }
}
