package com.flatts.recompile.compat.jade;

import com.flatts.recompile.Recompile;
import com.flatts.recompile.content.block.SortableBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade: how far a sortable block has been picked through. The {@code sorted} blockstate
 * is a hidden palette flyweight, so without this the player has no read on how close a
 * block is to crumbling. Only shown once at least one pull has happened.
 */
public enum SortProgressProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Identifier UID = Identifier.fromNamespaceAndPath(Recompile.MOD_ID, "sort_progress");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlock() instanceof SortableBlock sortable) {
            int pulls = sortable.sortedCount(accessor.getBlockState());
            if (pulls > 0) {
                tooltip.add(Component.translatable("jade.recompile.sorted",
                    pulls, sortable.sortCrumbleAt()));
            }
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
