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
 * Jade (client component) for the Trommel: power, queue depth, and why it is idle.
 *
 * <p>The idle line is the point, and this machine has one more reason to be idle than the Separator
 * does. It can be unpowered, it can be empty, or it can be holding something it cannot sort - the last
 * only after a datapack change under a saved world, but a player meeting it has no way at all to tell
 * it apart from the other two.
 *
 * <p>WHAT is inside is drawn as an item grid by {@link TrommelStorageProvider}; this line carries only
 * the total, the same division the Separator makes.
 */
public enum TrommelProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "trommel");

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
        int queued = data.getIntOr("queued", 0);

        tooltip.add(Component.translatable("jade.recompile.energy_stored",
            String.format("%,d", stored), String.format("%,d", capacity)));

        if (queued > 0) {
            tooltip.add(Component.translatable("jade.recompile.trommel_queued", queued));
        }

        if (goal > 0 && progress > 0) {
            tooltip.add(Component.translatable("jade.recompile.trommel_sorting",
                Math.min(99, progress * 100 / goal)));
        } else if (stored <= 0) {
            tooltip.add(Component.translatable("jade.recompile.trommel_no_power"));
        } else if (queued == 0) {
            tooltip.add(Component.translatable("jade.recompile.trommel_empty"));
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
