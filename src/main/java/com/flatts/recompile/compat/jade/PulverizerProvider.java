package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.PulverizerBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade (client component) for the Pulverizer: power, queue depth, the feed ratio, and why it is idle.
 *
 * <p>The idle line is the point, and this machine has one more reason to be idle than the Separator
 * does. It can be unpowered, it can be empty, or it can be holding something it cannot sort - the last
 * only after a datapack change under a saved world, but a player meeting it has no way at all to tell
 * it apart from the other two.
 *
 * <p>WHAT is inside is drawn as an item grid by {@link PulverizerStorageProvider}; this line carries only
 * the total, the same division the Separator makes.
 */
public enum PulverizerProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "pulverizer");

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
            tooltip.add(Component.translatable("jade.recompile.pulverizer_queued", queued));
        }

        // NOT ENOUGH OF IT YET, which is this machine's third reason to sit still and one the Trommel
        // cannot have: a recipe's count may exceed what is in the queue, so the mill holds a partial
        // stack rather than consuming it. Without this line that reads as a machine ignoring material
        // it plainly accepted.
        int have = data.getIntOr("have", 0);
        int need = data.getIntOr("need", 0);
        if (need > 1 && have > 0 && have < need) {
            tooltip.add(Component.translatable("jade.recompile.pulverizer_short", have, need));
        }

        // POWER FIRST, and the order is the fix. Asking "is it making progress" first meant a machine
        // that started a block and then lost its generator reported "Sorting 40%" forever: serverTick
        // sets goal and keeps progress BEFORE the energy check, so a stalled machine looks mid-run.
        // The tooltip claimed it was working while it had nothing to work with.
        //
        // AGAINST WHAT THE RUNNING RECIPE COSTS, which the machine now sends.
        //
        // Zero was too weak and reintroduced the defect the Trommel's comment records fixing: a buffer
        // holding 20 FE against a recipe wanting 24 is as stopped as an empty one, but `stored` is not
        // zero, so the progress branch won and the tooltip read "Milling: 40%" forever on a machine
        // that would never advance again. The Trommel can compare to a constant because its draw is
        // fixed; here the recipe declares it, so the recipe's number is the only correct threshold.
        // Falling back to 1 keeps "no power" meaning an empty buffer when nothing is running.
        int draw = Math.max(1, data.getIntOr("draw", 1));
        if (stored < draw) {
            tooltip.add(Component.translatable("jade.recompile.pulverizer_no_power"));
        } else if (queued == 0) {
            tooltip.add(Component.translatable("jade.recompile.pulverizer_empty"));
        } else {
            tooltip.add(Component.translatable("jade.recompile.pulverizer_milling",
                goal > 0 ? Math.min(99, progress * 100 / goal) : 0));
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
