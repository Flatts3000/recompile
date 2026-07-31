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
 * Jade (client component) for the power tier (#72): what a generator holds, and whether it is actually
 * producing right now.
 *
 * <p>Neither generator has a screen, and the Burner is fed by right-click, so <b>without this the buffer
 * is invisible</b> - a player has no way to tell a full generator from an empty one, or a panel in a spot
 * that works from one shaded by a block they forgot about. The rate line is the more useful half: "0 FE/t"
 * on a panel is the answer to "why is my machine not running", and it is otherwise unanswerable.
 *
 * <p>The Burner also shows its remaining burn in seconds rather than ticks, because the number a player
 * acts on is "do I need to feed it before I walk away".
 */
public enum GeneratorProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "generator");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains("capacity")) {
            return;
        }
        int stored = data.getIntOr("stored", 0);
        int capacity = data.getIntOr("capacity", 0);
        int rate = data.getIntOr("rate", 0);

        tooltip.add(Component.translatable("jade.recompile.energy_stored", format(stored), format(capacity)));
        if (rate > 0) {
            tooltip.add(Component.translatable("jade.recompile.energy_rate", rate));
        } else {
            tooltip.add(Component.translatable("jade.recompile.energy_idle"));
        }
        // Only the Burner sends this, so its absence is what distinguishes the two without a type check.
        if (data.contains("burn")) {
            int burn = data.getIntOr("burn", 0);
            if (burn > 0) {
                tooltip.add(Component.translatable("jade.recompile.burn_remaining", burn / 20));
            }
        }
    }

    /** Grouped digits ("4,096") read faster than a run of numerals at a glance. */
    private static Component format(int value) {
        return Component.literal(String.format("%,d", value));
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
