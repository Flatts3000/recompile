package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade (client component) for the Separator ({@code docs/gem_tier_spec.md}).
 *
 * <p><b>The idle line is the point.</b> A screenless machine that is not running has exactly two
 * reasons - no power, or not enough material above it - and a player standing in front of one cannot
 * tell which. That is the same question the Hydroponics Bay earned a whole GUI to answer; here it costs
 * a tooltip line, because the material is visible in the world and only the power is hidden.
 */
public enum SeparatorProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "separator");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains("capacity")) {
            return;
        }
        int stored = data.getIntOr("stored", 0);
        int capacity = data.getIntOr("capacity", 0);
        int progress = data.getIntOr("progress", 0);
        int goal = data.getIntOr("goal", 0);

        tooltip.add(Component.translatable("jade.recompile.energy_stored",
            String.format("%,d", stored), String.format("%,d", capacity)));

        // The queue depth first, because with an internal queue it is the thing that answers "did it
        // take what I put in" - which is the question a player standing in front of a silent machine is
        // actually asking. It is invisible otherwise: nothing can open this block.
        int queued = data.getIntOr("queued", 0);
        if (queued > 0) {
            int kinds = data.getIntOr("kinds", 0);
            tooltip.add(kinds > 1
                ? Component.translatable("jade.recompile.separator_queued_kinds", queued, kinds)
                : Component.translatable("jade.recompile.separator_queued", queued));
        }

        if (goal > 0 && progress > 0) {
            tooltip.add(Component.translatable("jade.recompile.separator_grinding",
                Math.min(99, progress * 100 / goal)));
        } else if (stored <= 0) {
            tooltip.add(Component.translatable("jade.recompile.separator_no_power"));
        } else if (queued == 0) {
            tooltip.add(Component.translatable("jade.recompile.separator_no_feed"));
        } else {
            int have = data.getIntOr("have", 0);
            int need = data.getIntOr("need", 0);
            if (need > have) {
                // The near-miss line, for a pack that sets a recipe count above 1. Ships unused at
                // 1-in-1-out, and stays because a machine that will not say which number it is waiting
                // for has hidden the only thing the player can act on.
                tooltip.add(Component.translatable("jade.recompile.separator_needs_more", have, need));
            }
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
