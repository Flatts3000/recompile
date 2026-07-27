package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.entity.TreeNurseryBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade (client component): the Tree Nursery's status - Running with a live seconds countdown, or Idle;
 * which species it is raising; and the water level. Reads the figures {@link TreeNurseryDataProvider}
 * sends. Fires on the core or its clad tank cell (the data provider resolves the core either way).
 */
public enum TreeNurseryProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID =
        Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "tree_nursery");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains("waterCap")) {
            return;
        }
        int cook = data.getIntOr("cook", 0);
        int total = Math.max(1, data.getIntOr("cookTotal", 1));

        if (cook > 0) {
            int seconds = (total - cook + 19) / 20;
            tooltip.add(Component.translatable("jade.recompile.nursery_running", seconds));
        } else {
            tooltip.add(Component.translatable("jade.recompile.nursery_idle"));
        }

        int species = data.getIntOr("species", 0);
        Item[] all = TreeNurseryBlockEntity.SPECIES;
        if (species >= 0 && species < all.length) {
            tooltip.add(Component.translatable("jade.recompile.nursery_species",
                new ItemStack(all[species]).getHoverName()));
        }
        // Water is left to Jade's built-in fluid readout (from the tank capability) - no duplicate line.
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
