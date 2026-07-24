package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade (client component): a Scrap Bin has no screen, so what it holds and how full it is are
 * otherwise invisible. Shows the bound material and its exact count against the capacity - "Scrap
 * Metal  320 / 4,096" - from the data {@link ScrapBinDataProvider} sends over. An empty, unbound bin
 * reads simply "Empty"; a bin emptied but still bound names what it is waiting to be refilled with.
 */
public enum ScrapBinProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "scrap_bin");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains("capacity")) {
            return;
        }
        int amount = data.getIntOr("amount", 0);
        int capacity = data.getIntOr("capacity", 0);
        Item material = data.getString("material")
            .map(id -> BuiltInRegistries.ITEM.getValue(Identifier.parse(id)))
            .orElse(null);

        if (material == null) {
            tooltip.add(Component.translatable("jade.recompile.scrap_bin_empty"));
            return;
        }
        Component name = Component.translatable(material.getDescriptionId());
        if (amount == 0) {
            tooltip.add(Component.translatable("jade.recompile.scrap_bin_bound", name));
        } else {
            tooltip.add(Component.translatable("jade.recompile.scrap_bin_amount",
                name, format(amount), format(capacity)));
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
