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
 * Jade (client component): the Compost Heap has no screen, so its progress is otherwise invisible.
 * Reads the totals {@link CompostHeapDataProvider} sends and shows how full it is and whether anything
 * is ready to pull - "Composting: 3 / 8" plus "1 layer ready". An empty formed heap reads "Empty".
 * Fires on any cell of the cage (the data provider resolves the core either way).
 */
public enum CompostHeapProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "compost_heap");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains("layers")) {
            return;
        }
        int layers = data.getIntOr("layers", 0);
        int max = data.getIntOr("max", 8);
        int ready = data.getIntOr("ready", 0);

        if (layers == 0) {
            tooltip.add(Component.translatable("jade.recompile.compost_empty"));
            return;
        }
        tooltip.add(Component.translatable("jade.recompile.compost_layers", layers, max));
        if (ready > 0) {
            tooltip.add(Component.translatable("jade.recompile.compost_ready", ready));
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
